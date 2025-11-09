package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record PhotoQueued(
    String photoId,
    String userId,
    String filename,
    long bytes,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoQueued";
    }
    
    @Override
    public String getAggregateId() {
        return photoId;
    }
    
    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }
}

