package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record ObjectRef(
    String bucket,
    String key,
    String region
) implements ValueObject {
    
    public ObjectRef {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket cannot be blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Region cannot be blank");
        }
    }
}

