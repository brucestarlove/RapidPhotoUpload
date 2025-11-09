package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.constraints.*;

public record FileUploadRequest(
    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename too long")
    @Pattern(regexp = "^[^/\\\\<>:\"|?*]+\\.(jpg|jpeg|png|gif|webp|heic|heif)$", 
             flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "Invalid filename or extension. Supported extensions: jpg, jpeg, png, gif, webp, heic, heif")
    String filename,
    
    @NotBlank(message = "MIME type is required")
    @Pattern(regexp = "^image/(jpeg|jpg|png|gif|webp|heic|heif)$", 
             message = "Unsupported MIME type. Supported types: image/jpeg, image/jpg, image/png, image/gif, image/webp, image/heic, image/heif")
    String mimeType,
    
    @Positive(message = "File size must be positive")
    @Max(value = 52428800, message = "File size exceeds maximum (50MB)")
    long bytes
) {}

