package com.starscape.rapidupload.features.uploadphoto.api.dto;

import java.util.List;

public record MultipartUploadInfo(
    String uploadId,
    long partSize,
    List<PartUrl> parts
) {}

