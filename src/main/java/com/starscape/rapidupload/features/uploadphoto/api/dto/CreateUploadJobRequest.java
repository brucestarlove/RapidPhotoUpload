package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUploadJobRequest(
    @NotEmpty(message = "Files list cannot be empty")
    @Size(max = 100, message = "Maximum 100 files per batch")
    @Valid
    List<FileUploadRequest> files,
    
    @NotNull(message = "Strategy is required")
    UploadStrategy strategy
) {}

