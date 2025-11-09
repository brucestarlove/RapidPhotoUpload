package com.starscape.rapidupload.features.trackprogress.api.dto;

import java.time.Instant;

/**
 * Job status update DTO for WebSocket broadcasts.
 * Sent when a job's overall status or progress changes.
 */
public record JobStatusUpdate(
    String jobId,
    String status,
    int totalCount,
    int completedCount,
    int failedCount,
    int cancelledCount,
    Instant timestamp
) {
    public static JobStatusUpdate of(
            String jobId, 
            String status, 
            int total, 
            int completed, 
            int failed, 
            int cancelled) {
        return new JobStatusUpdate(jobId, status, total, completed, failed, cancelled, Instant.now());
    }
}

