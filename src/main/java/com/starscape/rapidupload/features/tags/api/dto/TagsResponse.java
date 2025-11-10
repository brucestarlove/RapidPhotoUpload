package com.starscape.rapidupload.features.tags.api.dto;

import java.util.List;

/**
 * Response DTO for listing tags.
 */
public record TagsResponse(
    List<String> tags
) {}

