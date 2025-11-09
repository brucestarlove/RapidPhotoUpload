package com.starscape.rapidupload.features.getphotometadata.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for job status query.
 * Contains job information with all photo statuses.
 */
public record JobStatusResponse(
    String jobId,
    String status,
    int totalCount,
    int completedCount,
    int failedCount,
    int cancelledCount,
    List<PhotoStatusItem> photos,
    Instant createdAt,
    Instant updatedAt
) {}

