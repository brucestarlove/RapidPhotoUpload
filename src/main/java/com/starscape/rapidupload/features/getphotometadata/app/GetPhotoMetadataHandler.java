package com.starscape.rapidupload.features.getphotometadata.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoMetadataResponse;
import com.starscape.rapidupload.features.tags.domain.PhotoTag;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.domain.TagRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for retrieving photo metadata.
 * Returns full photo information including EXIF data, thumbnail URLs, and tags.
 */
@Service
public class GetPhotoMetadataHandler {
    
    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagRepository tagRepository;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;
    private final String bucket;
    
    public GetPhotoMetadataHandler(
            PhotoRepository photoRepository,
            PhotoTagRepository photoTagRepository,
            TagRepository tagRepository,
            S3Presigner s3Presigner,
            ObjectMapper objectMapper,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.tagRepository = tagRepository;
        this.s3Presigner = s3Presigner;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
    }
    
    @Transactional(readOnly = true)
    public PhotoMetadataResponse handle(String photoId, String userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Check if photo is soft-deleted - return 404 for deleted photos
        if (photo.isDeleted()) {
            throw new NotFoundException("Photo not found: " + photoId);
        }
        
        // Parse EXIF JSON
        Object exif = null;
        if (photo.getExifJson() != null) {
            try {
                exif = objectMapper.readValue(photo.getExifJson(), Object.class);
            } catch (JsonProcessingException e) {
                exif = photo.getExifJson();
            }
        }
        
        // Generate thumbnail URLs (presigned)
        List<String> thumbnailUrls = new ArrayList<>();
        if (photo.getS3Key() != null) {
            List<Integer> sizes = List.of(256, 1024);
            for (int size : sizes) {
                String thumbnailKey = getThumbnailKey(photo.getS3Key(), size);
                String url = generatePresignedGetUrl(thumbnailKey);
                thumbnailUrls.add(url);
            }
        }
        
        // Load tags for the photo
        List<String> tags = loadTagsForPhoto(photoId);
        
        return new PhotoMetadataResponse(
            photo.getPhotoId(),
            photo.getJobId(),
            photo.getFilename(),
            photo.getMimeType(),
            photo.getBytes(),
            photo.getStatus().name(),
            photo.getWidth(),
            photo.getHeight(),
            photo.getChecksum(),
            exif,
            thumbnailUrls,
            photo.getCreatedAt(),
            photo.getCompletedAt(),
            tags
        );
    }
    
    /**
     * Load tags for a single photo.
     */
    private List<String> loadTagsForPhoto(String photoId) {
        List<PhotoTag> photoTags = photoTagRepository.findByPhotoId(photoId);
        return photoTags.stream()
                .map(pt -> tagRepository.findById(pt.getTagId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(Tag::getLabel)
                .sorted()
                .collect(Collectors.toList());
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
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}

