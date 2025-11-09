package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event published when photo processing fails.
 * Includes error message and failure timestamp.
 */
public record PhotoFailed(
    String photoId,
    String userId,
    String jobId,
    String errorMessage,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoFailed";
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

