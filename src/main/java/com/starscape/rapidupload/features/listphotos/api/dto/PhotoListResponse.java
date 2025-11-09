package com.starscape.rapidupload.features.listphotos.api.dto;

import java.util.List;

/**
 * Response DTO for photo list query.
 * Contains paginated list of photos with pagination metadata.
 */
public record PhotoListResponse(
    List<PhotoListItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}

