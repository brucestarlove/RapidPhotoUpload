package com.starscape.rapidupload.features.getphotometadata.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for photo metadata query.
 * Contains full photo information including EXIF data and thumbnail URLs.
 */
public record PhotoMetadataResponse(
    String photoId,
    String jobId,
    String filename,
    String mimeType,
    long bytes,
    String status,
    Integer width,
    Integer height,
    String checksum,
    Object exif,
    List<String> thumbnailUrls,
    Instant createdAt,
    Instant completedAt
) {}

