package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record Progress(
    int percent,
    long bytesSent,
    long bytesTotal
) implements ValueObject {
    
    public Progress {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
        }
        if (bytesSent < 0 || bytesTotal < 0) {
            throw new IllegalArgumentException("Bytes cannot be negative");
        }
        if (bytesSent > bytesTotal) {
            throw new IllegalArgumentException("Bytes sent cannot exceed total");
        }
    }
    
    public static Progress of(long bytesSent, long bytesTotal) {
        int percent = bytesTotal > 0 ? (int) ((bytesSent * 100) / bytesTotal) : 0;
        return new Progress(percent, bytesSent, bytesTotal);
    }
}

