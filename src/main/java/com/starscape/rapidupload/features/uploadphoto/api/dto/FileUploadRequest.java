package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.constraints.*;

public record FileUploadRequest(
    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename too long")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+\\.(jpg|jpeg|png|gif|webp|heic|heif)$", 
             message = "Invalid filename or extension")
    String filename,
    
    @NotBlank(message = "MIME type is required")
    @Pattern(regexp = "^image/(jpeg|png|gif|webp|heic|heif)$", 
             message = "Unsupported MIME type")
    String mimeType,
    
    @Positive(message = "File size must be positive")
    @Max(value = 52428800, message = "File size exceeds maximum (50MB)")
    long bytes
) {}

