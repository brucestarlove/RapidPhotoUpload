package com.starscape.rapidupload.features.deletephoto.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for restoring soft-deleted photos.
 * Restores photo by clearing deletedAt timestamp.
 */
@Service
public class RestorePhotoHandler {
    
    private final PhotoRepository photoRepository;
    
    public RestorePhotoHandler(PhotoRepository photoRepository) {
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
        
        // Verify photo is deleted
        if (!photo.isDeleted()) {
            throw new IllegalArgumentException("Photo is not deleted");
        }
        
        // Restore photo
        photo.restore();
        photoRepository.save(photo);
    }
}

