package com.starscape.rapidupload.features.listphotos.app;

import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListItem;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import com.starscape.rapidupload.features.listphotos.infra.PhotoQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Handler for listing photos with pagination and filtering.
 * Supports filtering by status and searching by filename.
 */
@Service
public class ListPhotosHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ListPhotosHandler.class);
    
    private final PhotoQueryRepository photoQueryRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    
    public ListPhotosHandler(
            PhotoQueryRepository photoQueryRepository,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoQueryRepository = photoQueryRepository;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }
    
    @Transactional(readOnly = true)
    public PhotoListResponse handle(
            String userId, 
            String tag, 
            String status, 
            String query, 
            int page, 
            int size) {
        
        // Limit page size
        size = Math.min(size, 100);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Photo> photoPage;
        
        if (status != null && !status.isBlank()) {
            try {
                PhotoStatus photoStatus = PhotoStatus.valueOf(status.toUpperCase());
                photoPage = photoQueryRepository.findByUserIdAndStatus(userId, photoStatus, pageable);
            } catch (IllegalArgumentException e) {
                // Invalid status, fall back to all photos
                photoPage = photoQueryRepository.findByUserId(userId, pageable);
            }
        } else if (query != null && !query.isBlank()) {
            photoPage = photoQueryRepository.findByUserIdAndFilenameContaining(userId, query, pageable);
        } else {
            photoPage = photoQueryRepository.findByUserId(userId, pageable);
        }
        
        List<PhotoListItem> items = photoPage.getContent().stream()
                .map(this::toListItem)
                .toList();
        
        return new PhotoListResponse(
            items,
            photoPage.getNumber(),
            photoPage.getSize(),
            photoPage.getTotalElements(),
            photoPage.getTotalPages()
        );
    }
    
    /**
     * Convert Photo entity to PhotoListItem DTO.
     * Generates presigned thumbnail URLs for photos that have been uploaded.
     */
    private PhotoListItem toListItem(Photo photo) {
        String thumbnailUrl = null;
        
        // Generate thumbnail URL if photo has been uploaded (PROCESSING or COMPLETED)
        if (photo.getS3Key() != null && 
            (photo.getStatus() == PhotoStatus.PROCESSING || photo.getStatus() == PhotoStatus.COMPLETED)) {
            try {
                // Generate presigned URL for 256px thumbnail
                String thumbnailKey = getThumbnailKey(photo.getS3Key(), 256);
                thumbnailUrl = generatePresignedGetUrl(thumbnailKey);
                log.debug("Generated thumbnail URL for photo {}: {}", photo.getPhotoId(), thumbnailUrl);
            } catch (Exception e) {
                // If thumbnail doesn't exist or generation fails, try full image as fallback
                log.warn("Failed to generate thumbnail URL for photo {}: {}. Falling back to full image.", 
                    photo.getPhotoId(), e.getMessage());
                try {
                    // Fallback to full image if thumbnail doesn't exist yet
                    thumbnailUrl = generatePresignedGetUrl(photo.getS3Key());
                } catch (Exception e2) {
                    log.error("Failed to generate fallback image URL for photo {}: {}", 
                        photo.getPhotoId(), e2.getMessage());
                    thumbnailUrl = null;
                }
            }
        } else {
            log.debug("Skipping thumbnail URL generation for photo {}: status={}, s3Key={}", 
                photo.getPhotoId(), photo.getStatus(), photo.getS3Key());
        }
        
        return new PhotoListItem(
            photo.getPhotoId(),
            photo.getFilename(),
            photo.getMimeType(),
            photo.getBytes(),
            photo.getStatus().name(),
            photo.getWidth(),
            photo.getHeight(),
            thumbnailUrl,
            photo.getCreatedAt()
        );
    }
    
    /**
     * Generate thumbnail S3 key from original key.
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
     * Generate presigned GET URL for S3 object.
     */
    private String generatePresignedGetUrl(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))  // Longer duration for list view
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}

