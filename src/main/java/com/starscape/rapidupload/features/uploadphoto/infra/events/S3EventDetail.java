package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Detail section of an S3 EventBridge event.
 * Contains information about the S3 bucket and object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record S3EventDetail(
    @JsonProperty("version") String version,
    @JsonProperty("bucket") S3Bucket bucket,
    @JsonProperty("object") S3Object object,
    @JsonProperty("request-id") String requestId,
    @JsonProperty("requester") String requester,
    @JsonProperty("source-ip-address") String sourceIpAddress,
    @JsonProperty("reason") String reason
) {}

