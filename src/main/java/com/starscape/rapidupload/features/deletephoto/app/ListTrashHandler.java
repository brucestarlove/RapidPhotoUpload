package com.starscape.rapidupload.features.deletephoto.app;

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
 * Handler for listing soft-deleted photos (trash view).
 * Returns paginated list of deleted photos with tags and thumbnail URLs.
 */
@Service
public class ListTrashHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ListTrashHandler.class);
    
    private final PhotoQueryRepository photoQueryRepository;
    private final PhotoTagRepository photoTagRepository;
    private final TagRepository tagRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    
    public ListTrashHandler(
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
    public PhotoListResponse handle(String userId, int page, int size) {
        // Limit page size
        size = Math.min(size, 100);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        
        Page<Photo> photoPage = photoQueryRepository.findByUserIdAndDeletedAtIsNotNull(userId, pageable);
        
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
            tags,
            photo.getDeletedAt()
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
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}

