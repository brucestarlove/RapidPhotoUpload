package com.starscape.rapidupload.features.tags.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Junction entity for the many-to-many relationship between photos and tags.
 */
@Entity
@Table(name = "photo_tags")
@IdClass(PhotoTagId.class)
public class PhotoTag {
    
    @Id
    @Column(name = "photo_id", nullable = false)
    private String photoId;
    
    @Id
    @Column(name = "tag_id", nullable = false)
    private String tagId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    protected PhotoTag() {
        // JPA constructor
    }
    
    public PhotoTag(String photoId, String tagId) {
        if (photoId == null || photoId.isBlank()) {
            throw new IllegalArgumentException("Photo ID cannot be blank");
        }
        if (tagId == null || tagId.isBlank()) {
            throw new IllegalArgumentException("Tag ID cannot be blank");
        }
        
        this.photoId = photoId;
        this.tagId = tagId;
        this.createdAt = Instant.now();
    }
    
    // Getters
    public String getPhotoId() { return photoId; }
    public String getTagId() { return tagId; }
    public Instant getCreatedAt() { return createdAt; }
}

