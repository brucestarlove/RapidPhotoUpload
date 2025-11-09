package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProgressRequest(
    @NotBlank(message = "Photo ID is required")
    String photoId,
    
    @PositiveOrZero(message = "Bytes sent cannot be negative")
    long bytesSent,
    
    @PositiveOrZero(message = "Bytes total cannot be negative")
    long bytesTotal,
    
    @Min(value = 0, message = "Percent must be between 0 and 100")
    @Max(value = 100, message = "Percent must be between 0 and 100")
    int percent
) {}

