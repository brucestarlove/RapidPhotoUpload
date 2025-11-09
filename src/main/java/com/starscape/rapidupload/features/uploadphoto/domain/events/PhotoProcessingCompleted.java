package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event published when photo processing completes successfully.
 * Includes metadata such as dimensions, checksum, and completion timestamp.
 */
public record PhotoProcessingCompleted(
    String photoId,
    String userId,
    String jobId,
    int width,
    int height,
    String checksum,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoProcessingCompleted";
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

