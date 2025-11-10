package com.starscape.rapidupload.features.tags.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for PhotoTag entity.
 */
public class PhotoTagId implements Serializable {
    
    private String photoId;
    private String tagId;
    
    public PhotoTagId() {
        // JPA constructor
    }
    
    public PhotoTagId(String photoId, String tagId) {
        this.photoId = photoId;
        this.tagId = tagId;
    }
    
    public String getPhotoId() { return photoId; }
    public void setPhotoId(String photoId) { this.photoId = photoId; }
    
    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhotoTagId that = (PhotoTagId) o;
        return Objects.equals(photoId, that.photoId) && Objects.equals(tagId, that.tagId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(photoId, tagId);
    }
}

