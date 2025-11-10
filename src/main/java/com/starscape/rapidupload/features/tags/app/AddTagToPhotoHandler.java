package com.starscape.rapidupload.features.tags.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.tags.domain.PhotoTag;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.domain.TagRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for adding a tag to a photo.
 * Creates the tag if it doesn't exist, then creates the photo-tag association.
 */
@Service
public class AddTagToPhotoHandler {
    
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    
    public AddTagToPhotoHandler(
            PhotoRepository photoRepository,
            TagRepository tagRepository,
            PhotoTagRepository photoTagRepository) {
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.photoTagRepository = photoTagRepository;
    }
    
    @Transactional
    public void handle(String photoId, String userId, String tagLabel) {
        // Validate and normalize tag label
        String normalizedLabel = normalizeTagLabel(tagLabel);
        validateTagLabel(normalizedLabel);
        
        // Verify photo exists and belongs to user
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Find or create tag
        Tag tag = tagRepository.findByUserIdAndLabel(userId, normalizedLabel)
                .orElseGet(() -> {
                    String tagId = "tag_" + UUID.randomUUID().toString().replace("-", "");
                    Tag newTag = new Tag(tagId, userId, normalizedLabel);
                    return tagRepository.save(newTag);
                });
        
        // Create photo-tag association if it doesn't exist
        if (!photoTagRepository.existsByPhotoIdAndTagId(photoId, tag.getTagId())) {
            PhotoTag photoTag = new PhotoTag(photoId, tag.getTagId());
            photoTagRepository.save(photoTag);
        }
    }
    
    /**
     * Normalize tag label: trim whitespace.
     */
    private String normalizeTagLabel(String label) {
        return label.trim();
    }
    
    /**
     * Validate tag label format and length.
     */
    private void validateTagLabel(String label) {
        if (label.length() > 50) {
            throw new IllegalArgumentException("Tag label must be 50 characters or less");
        }
        
        // Validate format: alphanumeric, spaces, hyphens, underscores
        if (!label.matches("^[a-zA-Z0-9\\s\\-_]+$")) {
            throw new IllegalArgumentException("Tag label can only contain alphanumeric characters, spaces, hyphens, and underscores");
        }
    }
}

