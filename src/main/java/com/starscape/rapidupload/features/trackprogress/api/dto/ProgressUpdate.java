package com.starscape.rapidupload.features.trackprogress.api.dto;

import java.time.Instant;

/**
 * Progress update DTO for WebSocket broadcasts.
 * Sent when a photo's status changes.
 */
public record ProgressUpdate(
    String jobId,
    String photoId,
    String status,
    int progressPercent,
    String message,
    Instant timestamp
) {
    public static ProgressUpdate of(String jobId, String photoId, String status, int percent, String message) {
        return new ProgressUpdate(jobId, photoId, status, percent, message, Instant.now());
    }
}

