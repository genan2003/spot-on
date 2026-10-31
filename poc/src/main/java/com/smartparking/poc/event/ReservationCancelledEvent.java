package com.smartparking.poc.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** <b>Observer pattern: Event.</b> A reservation was cancelled, by the driver or because an admin closed its spot. */
@Getter
public class ReservationCancelledEvent extends DomainEvent {

    private final UUID reservationId;
    private final UUID driverId;
    private final UUID spotId;
    private final String spotCode;
    /** True when an admin took the spot out of service, false when the driver cancelled. */
    private final boolean spotClosed;

    public ReservationCancelledEvent(Instant occurredAt, UUID reservationId, UUID driverId, UUID spotId, String spotCode,
            boolean spotClosed) {
        super(occurredAt);
        this.reservationId = reservationId;
        this.driverId = driverId;
        this.spotId = spotId;
        this.spotCode = spotCode;
        this.spotClosed = spotClosed;
    }
}
