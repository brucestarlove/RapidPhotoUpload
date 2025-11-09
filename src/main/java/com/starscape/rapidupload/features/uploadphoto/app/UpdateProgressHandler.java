package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.uploadphoto.api.dto.UpdateProgressRequest;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProgressHandler {
    
    private static final Logger log = LoggerFactory.getLogger(UpdateProgressHandler.class);
    
    private final PhotoRepository photoRepository;
    
    public UpdateProgressHandler(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }
    
    @Transactional
    public void handle(UpdateProgressRequest request, String userId) {
        Photo photo = photoRepository.findById(request.photoId())
                .orElseThrow(() -> new NotFoundException("Photo not found: " + request.photoId()));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Best-effort: mark as uploading if progress is being reported
        if (request.percent() > 0 && request.percent() < 100) {
            photo.markUploading();
            photoRepository.save(photo);
            log.debug("Photo {} progress: {}%", request.photoId(), request.percent());
        }
    }
}

