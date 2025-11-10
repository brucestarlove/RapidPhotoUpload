package com.starscape.rapidupload.features.deletephoto.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for soft-deleting photos.
 * Marks photo as deleted by setting deletedAt timestamp.
 */
@Service
public class DeletePhotoHandler {
    
    private final PhotoRepository photoRepository;
    
    public DeletePhotoHandler(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }
    
    @Transactional
    public void handle(String photoId, String userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Check if already deleted
        if (photo.isDeleted()) {
            // Already deleted, no-op
            return;
        }
        
        // Mark as deleted
        photo.markDeleted();
        photoRepository.save(photo);
    }
}

