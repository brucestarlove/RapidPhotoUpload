package com.starscape.rapidupload.features.uploadphoto.api.dto;

public record PartUrl(
    int partNumber,
    String url,
    long size
) {}

