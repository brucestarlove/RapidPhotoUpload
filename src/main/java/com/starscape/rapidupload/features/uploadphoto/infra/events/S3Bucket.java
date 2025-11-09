package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * S3 bucket information from an EventBridge event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record S3Bucket(
    @JsonProperty("name") String name
) {}

