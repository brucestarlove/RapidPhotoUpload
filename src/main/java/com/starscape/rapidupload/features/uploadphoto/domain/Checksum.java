package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record Checksum(
    String algorithm,
    String value
) implements ValueObject {
    
    public Checksum {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Algorithm cannot be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be blank");
        }
    }
    
    public static Checksum sha256(String value) {
        return new Checksum("SHA-256", value);
    }
}

