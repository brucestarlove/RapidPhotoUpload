package com.starscape.rapidupload.features.listphotos.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for a photo item in a list response.
 * Contains essential photo information for listing views.
 * deletedAt is null for non-deleted photos, set for deleted photos (trash view).
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
    Instant createdAt,
    List<String> tags,
    Instant deletedAt
) {}

