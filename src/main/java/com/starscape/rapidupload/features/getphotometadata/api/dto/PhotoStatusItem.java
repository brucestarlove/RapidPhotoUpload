package com.starscape.rapidupload.features.getphotometadata.api.dto;

/**
 * DTO for a photo status item within a job status response.
 */
public record PhotoStatusItem(
    String photoId,
    String filename,
    String status,
    String errorMessage
) {}

