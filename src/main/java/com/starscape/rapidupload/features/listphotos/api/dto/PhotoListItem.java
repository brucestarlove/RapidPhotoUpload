package com.starscape.rapidupload.features.listphotos.api.dto;

import java.time.Instant;

/**
 * DTO for a photo item in a list response.
 * Contains essential photo information for listing views.
 */
public record PhotoListItem(
    String photoId,
    String filename,
    String mimeType,
    long bytes,
    String status,
    Integer width,
    Integer height,
    String thumbnailUrl,
    Instant createdAt
) {}

