package com.smartparking.poc.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * <b>Observer pattern: Event</b> passed from publisher to listeners. {@code eventId} lets a
 * listener ignore an event it has already handled (needed once events travel over RabbitMQ).
 */
@Getter
public abstract class DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredAt;

    protected DomainEvent(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
