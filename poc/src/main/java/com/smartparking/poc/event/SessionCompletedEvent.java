package com.smartparking.poc.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** <b>Observer pattern: Event.</b> A parking session ended and its cost is known. */
@Getter
public class SessionCompletedEvent extends DomainEvent {

    private final UUID sessionId;
    private final UUID driverId;
    private final String spotCode;
    private final BigDecimal cost;

    public SessionCompletedEvent(Instant occurredAt, UUID sessionId, UUID driverId, String spotCode, BigDecimal cost) {
        super(occurredAt);
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.spotCode = spotCode;
        this.cost = cost;
    }
}
