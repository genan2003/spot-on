package com.smartparking.poc.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** <b>Observer pattern: Event.</b> A reservation was cancelled. */
@Getter
public class ReservationCancelledEvent extends DomainEvent {

    private final UUID reservationId;
    private final UUID driverId;
    private final UUID spotId;
    private final String spotCode;

    public ReservationCancelledEvent(Instant occurredAt, UUID reservationId, UUID driverId, UUID spotId, String spotCode) {
        super(occurredAt);
        this.reservationId = reservationId;
        this.driverId = driverId;
        this.spotId = spotId;
        this.spotCode = spotCode;
    }
}
