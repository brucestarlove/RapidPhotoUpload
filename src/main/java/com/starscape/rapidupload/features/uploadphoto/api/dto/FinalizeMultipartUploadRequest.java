package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for finalizing a multipart upload.
 * Contains the upload ID and list of completed parts with their ETags.
 */
public record FinalizeMultipartUploadRequest(
    @NotBlank(message = "Upload ID is required")
    String uploadId,
    
    @NotEmpty(message = "Parts list cannot be empty")
    @Valid
    List<CompletedPart> parts
) {
    /**
     * Represents a completed part with its part number and ETag.
     * The ETag is returned by S3 after uploading each part.
     */
    public record CompletedPart(
        int partNumber,
        @NotBlank(message = "ETag is required")
        String etag
    ) {}
}

