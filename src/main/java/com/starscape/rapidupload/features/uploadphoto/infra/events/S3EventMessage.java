package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EventBridge message wrapper for S3 events.
 * This represents the top-level structure of an EventBridge event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record S3EventMessage(
    @JsonProperty("version") String version,
    @JsonProperty("id") String id,
    @JsonProperty("detail-type") String detailType,
    @JsonProperty("source") String source,
    @JsonProperty("time") String time,
    @JsonProperty("detail") S3EventDetail detail
) {}

