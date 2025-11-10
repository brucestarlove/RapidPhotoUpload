package com.starscape.rapidupload.features.tags.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tag entity representing a user-created tag.
 * Tags are user-specific and can be applied to multiple photos.
 */
@Entity
@Table(name = "tags", uniqueConstraints = {
    @UniqueConstraint(name = "tags_user_label_unique", columnNames = {"user_id", "label"})
})
public class Tag extends com.starscape.rapidupload.common.domain.Entity<String> {
    
    @Id
    @Column(name = "tag_id")
    private String tagId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(nullable = false, length = 100)
    private String label;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    protected Tag() {
        // JPA constructor
    }
    
    public Tag(String tagId, String userId, String label) {
        super(tagId);
        validateInput(tagId, userId, label);
        
        this.tagId = tagId;
        this.userId = userId;
        this.label = normalizeLabel(label);
        this.createdAt = Instant.now();
    }
    
    private void validateInput(String tagId, String userId, String label) {
        if (tagId == null || tagId.isBlank()) {
            throw new IllegalArgumentException("Tag ID cannot be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Tag label cannot be blank");
        }
        
        String normalized = normalizeLabel(label);
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Tag label must be 50 characters or less");
        }
        
        // Validate format: alphanumeric, spaces, hyphens, underscores
        if (!normalized.matches("^[a-zA-Z0-9\\s\\-_]+$")) {
            throw new IllegalArgumentException("Tag label can only contain alphanumeric characters, spaces, hyphens, and underscores");
        }
    }
    
    /**
     * Normalize tag label: trim whitespace and convert to lowercase.
     */
    private String normalizeLabel(String label) {
        return label.trim();
    }
    
    @Override
    public String getId() {
        return tagId;
    }
    
    // Getters
    public String getTagId() { return tagId; }
    public String getUserId() { return userId; }
    public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
}

