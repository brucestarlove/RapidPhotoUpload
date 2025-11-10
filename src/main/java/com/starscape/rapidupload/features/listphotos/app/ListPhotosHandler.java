package com.starscape.rapidupload.features.listphotos.app;

import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListItem;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.tags.domain.PhotoTag;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.domain.TagRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for listing photos with pagination and filtering.
 * Supports filtering by status, tag, and searching by filename.
 */
@Service
public class ListPhotosHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ListPhotosHandler.class);
    
    private final PhotoQueryRepository photoQueryRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagRepository tagRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    
    public ListPhotosHandler(
            PhotoQueryRepository photoQueryRepository,
            PhotoTagRepository photoTagRepository,
            TagRepository tagRepository,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoQueryRepository = photoQueryRepository;
        this.photoTagRepository = photoTagRepository;
        this.tagRepository = tagRepository;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }
    
    @Transactional(readOnly = true)
    public PhotoListResponse handle(
            String userId, 
            String tag, 
            String status, 
            String search, 
            int page, 
            int size) {
        
        // Limit page size
        size = Math.min(size, 100);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Photo> photoPage;
        
        // Normalize tag label if provided
        String tagLabel = (tag != null && !tag.isBlank()) ? tag.trim() : null;
        boolean hasTagFilter = tagLabel != null;
        boolean hasStatusFilter = status != null && !status.isBlank();
        boolean hasSearchFilter = search != null && !search.isBlank();
        
        PhotoStatus photoStatus = null;
        if (hasStatusFilter) {
            try {
                photoStatus = PhotoStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore filter
                hasStatusFilter = false;
            }
        }
        
        // Determine which query method to use based on filters
        if (hasStatusFilter && hasTagFilter && hasSearchFilter) {
            photoPage = photoQueryRepository.findByUserIdAndStatusAndTagAndFilenameContaining(
                userId, photoStatus, tagLabel, search, pageable);
        } else if (hasStatusFilter && hasTagFilter) {
            photoPage = photoQueryRepository.findByUserIdAndStatusAndTag(
                userId, photoStatus, tagLabel, pageable);
        } else if (hasTagFilter && hasSearchFilter) {
            photoPage = photoQueryRepository.findByUserIdAndTagAndFilenameContaining(
                userId, tagLabel, search, pageable);
        } else if (hasTagFilter) {
            photoPage = photoQueryRepository.findByUserIdAndTag(userId, tagLabel, pageable);
        } else if (hasStatusFilter) {
            photoPage = photoQueryRepository.findByUserIdAndStatus(userId, photoStatus, pageable);
        } else if (hasSearchFilter) {
            photoPage = photoQueryRepository.findByUserIdAndFilenameContaining(userId, search, pageable);
        } else {
            photoPage = photoQueryRepository.findByUserId(userId, pageable);
        }
        
        // Load tags for all photos in one batch
        List<String> photoIds = photoPage.getContent().stream()
                .map(Photo::getPhotoId)
                .toList();
        Map<String, List<String>> tagsByPhotoId = loadTagsForPhotos(photoIds);
        
        List<PhotoListItem> items = photoPage.getContent().stream()
                .map(photo -> toListItem(photo, tagsByPhotoId.getOrDefault(photo.getPhotoId(), List.of())))
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
     * Load tags for multiple photos efficiently.
     * Returns a map of photoId -> list of tag labels.
     */
    private Map<String, List<String>> loadTagsForPhotos(List<String> photoIds) {
        if (photoIds.isEmpty()) {
            return Map.of();
        }
        
        // Load all photo-tag associations for these photos
        List<PhotoTag> photoTags = new ArrayList<>();
        for (String photoId : photoIds) {
            photoTags.addAll(photoTagRepository.findByPhotoId(photoId));
        }
        
        // Load all tags
        List<String> tagIds = photoTags.stream()
                .map(PhotoTag::getTagId)
                .distinct()
                .toList();
        
        Map<String, String> tagLabelsById = tagIds.stream()
                .map(tagRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toMap(Tag::getTagId, Tag::getLabel));
        
        // Group tags by photo ID
        return photoTags.stream()
                .collect(Collectors.groupingBy(
                    PhotoTag::getPhotoId,
                    Collectors.mapping(
                        pt -> tagLabelsById.get(pt.getTagId()),
                        Collectors.toList()
                    )
                ));
    }
    
    /**
     * Convert Photo entity to PhotoListItem DTO.
     * Generates presigned thumbnail URLs for photos that have been uploaded.
     */
    private PhotoListItem toListItem(Photo photo, List<String> tags) {
        String thumbnailUrl = null;
        
        // Generate thumbnail URL if photo has been uploaded (PROCESSING or COMPLETED)
        if (photo.getS3Key() != null && 
            (photo.getStatus() == PhotoStatus.PROCESSING || photo.getStatus() == PhotoStatus.COMPLETED)) {
            try {
                // Generate presigned URL for 256px thumbnail
                String thumbnailKey = getThumbnailKey(photo.getS3Key(), 256);
                log.debug("Generating presigned URL for thumbnail: bucket={}, key={}", bucket, thumbnailKey);
                thumbnailUrl = generatePresignedGetUrl(thumbnailKey);
                log.info("✅ Generated presigned thumbnail URL for photo {}: {}", photo.getPhotoId(), thumbnailUrl);
            } catch (Exception e) {
                // If thumbnail doesn't exist or generation fails, try full image as fallback
                log.warn("❌ Failed to generate thumbnail URL for photo {}: {}. Falling back to full image.", 
                    photo.getPhotoId(), e.getMessage(), e);
                try {
                    // Fallback to full image if thumbnail doesn't exist yet
                    thumbnailUrl = generatePresignedGetUrl(photo.getS3Key());
                    log.info("✅ Generated fallback presigned URL for photo {}: {}", photo.getPhotoId(), thumbnailUrl);
                } catch (Exception e2) {
                    log.error("❌ Failed to generate fallback image URL for photo {}: {}", 
                        photo.getPhotoId(), e2.getMessage(), e2);
                    thumbnailUrl = null;
                }
            }
        } else {
            log.debug("⏭️ Skipping thumbnail URL generation for photo {}: status={}, s3Key={}", 
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
            photo.getCreatedAt(),
            tags
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
        if (s3Presigner == null) {
            log.error("❌ S3Presigner is null! Cannot generate presigned URL.");
            throw new IllegalStateException("S3Presigner is not configured");
        }
        
        if (bucket == null || bucket.isBlank()) {
            log.error("❌ S3 bucket is not configured!");
            throw new IllegalStateException("S3 bucket is not configured");
        }
        
        log.debug("Generating presigned URL: bucket={}, key={}", bucket, s3Key);
        
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))  // Longer duration for list view
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        String url = presigned.url().toString();
        log.debug("Generated presigned URL: {}", url);
        return url;
    }
}

