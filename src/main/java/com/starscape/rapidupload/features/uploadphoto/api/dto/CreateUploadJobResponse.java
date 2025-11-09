package com.starscape.rapidupload.features.uploadphoto.api.dto;

import java.util.List;

public record CreateUploadJobResponse(
    String jobId,
    List<PhotoUploadItem> items
) {}

