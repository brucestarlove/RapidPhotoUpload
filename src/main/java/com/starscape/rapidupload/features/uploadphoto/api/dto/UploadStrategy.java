package com.starscape.rapidupload.features.uploadphoto.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UploadStrategy {
    S3_PRESIGNED("s3-presigned"),
    S3_MULTIPART("s3-multipart");
    
    private final String jsonValue;
    
    UploadStrategy(String jsonValue) {
        this.jsonValue = jsonValue;
    }
    
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }
    
    @JsonCreator
    public static UploadStrategy fromString(String value) {
        if (value == null) {
            return null;
        }
        
        // Try exact match first (for backward compatibility with tests)
        for (UploadStrategy strategy : values()) {
            if (strategy.name().equals(value) || strategy.jsonValue.equals(value)) {
                return strategy;
            }
        }
        
        // Try case-insensitive match
        String normalized = value.toUpperCase().replace("-", "_");
        for (UploadStrategy strategy : values()) {
            if (strategy.name().equals(normalized)) {
                return strategy;
            }
        }
        
        throw new IllegalArgumentException("Unknown UploadStrategy: " + value);
    }
}

