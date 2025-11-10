package com.starscape.rapidupload.features.tags.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.domain.TagRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for removing a tag from a photo.
 * Verifies photo ownership before removing the tag association.
 */
@Service
public class RemoveTagFromPhotoHandler {
    
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    
    public RemoveTagFromPhotoHandler(
            PhotoRepository photoRepository,
            TagRepository tagRepository,
            PhotoTagRepository photoTagRepository) {
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.photoTagRepository = photoTagRepository;
    }
    
    @Transactional
    public void handle(String photoId, String userId, String tagLabel) {
        // Normalize tag label
        String normalizedLabel = normalizeTagLabel(tagLabel);
        
        // Verify photo exists and belongs to user
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Find tag
        Tag tag = tagRepository.findByUserIdAndLabel(userId, normalizedLabel)
                .orElseThrow(() -> new NotFoundException("Tag not found: " + normalizedLabel));
        
        // Remove photo-tag association
        photoTagRepository.deleteByPhotoIdAndTagId(photoId, tag.getTagId());
    }
    
    /**
     * Normalize tag label: trim whitespace.
     */
    private String normalizeTagLabel(String label) {
        return label.trim();
    }
}

