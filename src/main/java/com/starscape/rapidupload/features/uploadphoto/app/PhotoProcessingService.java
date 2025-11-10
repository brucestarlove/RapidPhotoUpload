package com.starscape.rapidupload.features.uploadphoto.app;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxService;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.starscape.rapidupload.common.config.ProcessingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for processing uploaded photos:
 * - Extracts EXIF metadata
 * - Generates thumbnails
 * - Computes SHA-256 checksums
 * - Updates photo status
 */
@Service
public class PhotoProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingService.class);
    
    private final PhotoRepository photoRepository;
    private final S3Client s3Client;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final List<Integer> thumbnailSizes;
    
    public PhotoProcessingService(
            PhotoRepository photoRepository,
            S3Client s3Client,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            ProcessingProperties processingProperties,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoRepository = photoRepository;
        this.s3Client = s3Client;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.thumbnailSizes = processingProperties.getThumbnailSizes();
    }
    
    /**
     * Process a photo uploaded to S3.
     * This method is idempotent - it will skip processing if the photo is already completed.
     * 
     * @param s3Key The S3 key of the uploaded photo
     * @param etag The ETag from S3
     * @param size The size of the object in bytes
     */
    @Transactional
    public void processPhoto(String s3Key, String etag, long size) {
        log.info("Processing photo: s3Key={}, etag={}, size={}", s3Key, etag, size);
        
        // Extract photoId from S3 key: dev/userId/jobId/photoId.ext
        String photoId = extractPhotoIdFromS3Key(s3Key);
        if (photoId == null) {
            log.warn("Could not extract photoId from S3 key: {}", s3Key);
            return;
        }
        
        // Find photo by ID (s3_key is NULL until processing starts)
        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) {
            log.warn("Photo not found for photoId: {} (S3 key: {})", photoId, s3Key);
            return;
        }
        
        Photo photo = photoOpt.get();
        
        // Idempotency check: skip if already completed
        if (photo.getStatus() == PhotoStatus.COMPLETED) {
            log.info("Photo already processed: {}", photo.getPhotoId());
            return;
        }
        
        try {
            // Mark as processing
            photo.markProcessing(s3Key, bucket, etag);
            photoRepository.save(photo);
            
            // Download image from S3
            byte[] imageBytes = downloadFromS3(s3Key);
            
            // Compute checksum
            String checksum = DigestUtils.sha256Hex(imageBytes);
            
            // Extract EXIF metadata
            Map<String, Object> exifData = extractExif(imageBytes);
            String exifJson = objectMapper.writeValueAsString(exifData);
            
            // Read image dimensions
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IOException("Failed to read image");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Generate thumbnails
            generateThumbnails(s3Key, imageBytes, photo.getMimeType());
            
            // Mark completed
            photo.markCompleted(width, height, exifJson, checksum);
            photoRepository.save(photo);
            
            // Publish event
            PhotoProcessingCompleted event = new PhotoProcessingCompleted(
                photo.getPhotoId(),
                photo.getUserId(),
                photo.getJobId(),
                width,
                height,
                checksum,
                Instant.now()
            );
            outboxService.publish(event, "Photo");
            
            log.info("Photo processed successfully: {}", photo.getPhotoId());
            
        } catch (Exception e) {
            log.error("Failed to process photo: {}", photo.getPhotoId(), e);
            photo.markFailed(e.getMessage());
            photoRepository.save(photo);
            
            // Publish failure event
            PhotoFailed event = new PhotoFailed(
                photo.getPhotoId(),
                photo.getUserId(),
                photo.getJobId(),
                e.getMessage(),
                Instant.now()
            );
            outboxService.publish(event, "Photo");
        }
    }
    
    /**
     * Download image bytes from S3.
     */
    private byte[] downloadFromS3(String s3Key) throws IOException {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
            return response.readAllBytes();
        }
    }
    
    /**
     * Extract EXIF metadata from image bytes.
     * Returns a map of directory names to tag maps.
     * Sanitizes string values to remove null bytes, which PostgreSQL JSONB doesn't support.
     */
    private Map<String, Object> extractExif(byte[] imageBytes) {
        Map<String, Object> exifData = new HashMap<>();
        
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            
            for (Directory directory : metadata.getDirectories()) {
                String directoryName = sanitizeString(directory.getName());
                Map<String, String> tags = new HashMap<>();
                
                for (Tag tag : directory.getTags()) {
                    String tagName = sanitizeString(tag.getTagName());
                    String tagDescription = sanitizeString(tag.getDescription());
                    tags.put(tagName, tagDescription);
                }
                
                if (!tags.isEmpty()) {
                    exifData.put(directoryName, tags);
                }
            }
            
        } catch (ImageProcessingException | IOException e) {
            // Log at debug level - EXIF extraction failures are expected for invalid/corrupted images
            log.debug("Failed to extract EXIF data: {}", e.getMessage());
            exifData.put("error", sanitizeString(e.getMessage()));
        }
        
        return exifData;
    }
    
    /**
     * Sanitize a string by removing null bytes (\u0000).
     * PostgreSQL JSONB doesn't support null bytes in text, so we remove them.
     * 
     * @param str The string to sanitize
     * @return The sanitized string with null bytes removed, or null if input is null
     */
    private String sanitizeString(String str) {
        if (str == null) {
            return null;
        }
        // Remove null bytes - PostgreSQL JSONB doesn't support them
        return str.replace("\u0000", "");
    }
    
    /**
     * Generate thumbnails for the given image and upload them to S3.
     */
    private void generateThumbnails(String originalKey, byte[] imageBytes, String mimeType) {
        for (int size : thumbnailSizes) {
            try {
                // Generate thumbnail
                ByteArrayOutputStream thumbOutput = new ByteArrayOutputStream();
                Thumbnails.of(new ByteArrayInputStream(imageBytes))
                        .size(size, size)
                        .outputFormat(getFormatFromMimeType(mimeType))
                        .toOutputStream(thumbOutput);
                
                byte[] thumbnailBytes = thumbOutput.toByteArray();
                
                // Upload to S3 under thumbnails/ prefix
                String thumbnailKey = getThumbnailKey(originalKey, size);
                uploadThumbnailToS3(thumbnailKey, thumbnailBytes, mimeType);
                
                log.debug("Generated thumbnail: size={}, key={}", size, thumbnailKey);
                
            } catch (IOException e) {
                log.error("Failed to generate thumbnail: size={}", size, e);
            }
        }
    }
    
    /**
     * Generate thumbnail S3 key from original key.
     * Original: env/userId/jobId/photoId.ext
     * Thumbnail: env/userId/jobId/thumbnails/photoId_256.ext
     */
    private String getThumbnailKey(String originalKey, int size) {
        int lastSlash = originalKey.lastIndexOf('/');
        String basePath = originalKey.substring(0, lastSlash);
        String filename = originalKey.substring(lastSlash + 1);
        
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        return basePath + "/thumbnails/" + name + "_" + size + ext;
    }
    
    /**
     * Upload thumbnail to S3.
     */
    private void uploadThumbnailToS3(String key, byte[] data, String mimeType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(mimeType)
                .contentLength((long) data.length)
                .build();
        
        s3Client.putObject(putRequest, RequestBody.fromBytes(data));
    }
    
    /**
     * Convert MIME type to image format string for Thumbnailator.
     */
    private String getFormatFromMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
    
    /**
     * Extract photoId from S3 key.
     * S3 key format: dev/userId/jobId/photoId.ext
     * Returns the photoId (without extension).
     */
    private String extractPhotoIdFromS3Key(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return null;
        }
        
        // Get the filename (last part after last slash)
        int lastSlash = s3Key.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == s3Key.length() - 1) {
            return null;
        }
        
        String filename = s3Key.substring(lastSlash + 1);
        
        // Remove extension to get photoId
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        
        return filename;
    }
}

