package com.starscape.rapidupload.features.deletephoto.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.deletephoto.infra.S3CleanupService;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Handler for permanently deleting photos.
 * Only allows deletion if photo was soft-deleted more than 7 days ago.
 * Deletes S3 objects and database records.
 */
@Service
public class PermanentDeleteHandler {
    
    private static final Logger log = LoggerFactory.getLogger(PermanentDeleteHandler.class);
    private static final int RETENTION_DAYS = 7;
    
    private final PhotoRepository photoRepository;
    private final PhotoTagRepository photoTagRepository;
    private final S3CleanupService s3CleanupService;
    
    public PermanentDeleteHandler(
            PhotoRepository photoRepository,
            PhotoTagRepository photoTagRepository,
            S3CleanupService s3CleanupService) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.s3CleanupService = s3CleanupService;
    }
    
    @Transactional
    public void handle(String photoId, String userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Verify photo is deleted
        if (!photo.isDeleted()) {
            throw new IllegalArgumentException("Photo must be soft-deleted before permanent deletion");
        }
        
        // Verify 7-day retention period has passed
        Instant cutoffDate = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        if (photo.getDeletedAt().isAfter(cutoffDate)) {
            long daysRemaining = ChronoUnit.DAYS.between(photo.getDeletedAt(), cutoffDate) + RETENTION_DAYS;
            throw new IllegalArgumentException(
                String.format("Photo cannot be permanently deleted yet. %d days remaining in retention period.", daysRemaining));
        }
        
        // Delete photo-tag associations (cascade should handle this, but explicit for safety)
        List<com.starscape.rapidupload.features.tags.domain.PhotoTag> photoTags = 
            photoTagRepository.findByPhotoId(photoId);
        for (var photoTag : photoTags) {
            photoTagRepository.delete(photoTag);
        }
        
        // Delete S3 objects (original and thumbnails)
        if (photo.getS3Key() != null) {
            boolean s3Deleted = s3CleanupService.deletePhotoAndThumbnails(photo.getS3Key());
            if (!s3Deleted) {
                log.warn("Some S3 objects failed to delete for photo {}, but proceeding with database deletion", photoId);
            }
        }
        
        // Delete photo record from database
        photoRepository.delete(photo);
        log.info("Permanently deleted photo: photoId={}, userId={}", photoId, userId);
    }
}

