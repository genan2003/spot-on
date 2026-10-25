package com.smartparking.poc.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** <b>Observer pattern: Event.</b> A payment for a session completed. */
@Getter
public class PaymentCompletedEvent extends DomainEvent {

    private final UUID paymentId;
    private final UUID sessionId;
    private final UUID driverId;
    private final BigDecimal amount;

    public PaymentCompletedEvent(Instant occurredAt, UUID paymentId, UUID sessionId, UUID driverId, BigDecimal amount) {
        super(occurredAt);
        this.paymentId = paymentId;
        this.sessionId = sessionId;
        this.driverId = driverId;
        this.amount = amount;
    }
}
