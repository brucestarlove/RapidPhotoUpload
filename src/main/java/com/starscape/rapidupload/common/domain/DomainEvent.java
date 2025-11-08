package com.starscape.rapidupload.common.domain;

import java.time.Instant;

public interface DomainEvent {
    String getEventType();
    String getAggregateId();
    Instant getOccurredOn();
}

