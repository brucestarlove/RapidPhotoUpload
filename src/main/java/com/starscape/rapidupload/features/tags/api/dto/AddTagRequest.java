package com.starscape.rapidupload.features.tags.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a tag to a photo.
 */
public record AddTagRequest(
    @NotBlank(message = "Tag label is required")
    @Size(max = 50, message = "Tag label must be 50 characters or less")
    String tag
) {}

