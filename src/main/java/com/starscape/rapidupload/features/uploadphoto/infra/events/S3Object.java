package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * S3 object information from an EventBridge event.
 * Contains the key, size, ETag, and sequencer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record S3Object(
    @JsonProperty("key") String key,
    @JsonProperty("size") long size,
    @JsonProperty("etag") String etag,
    @JsonProperty("sequencer") String sequencer
) {}

