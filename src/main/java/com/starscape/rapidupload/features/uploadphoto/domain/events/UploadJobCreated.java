package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record UploadJobCreated(
    String jobId,
    String userId,
    int totalCount,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "UploadJobCreated";
    }
    
    @Override
    public String getAggregateId() {
        return jobId;
    }
    
    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }
}

