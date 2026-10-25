package com.smartparking.poc.reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(UUID id, UUID driverId, UUID spotId, String spotCode, ReservationStatus status,
        Instant createdAt, Instant expiresAt) {

    static ReservationResponse from(Reservation r) {
        return new ReservationResponse(r.getId(), r.getDriver().getId(), r.getSpot().getId(), r.getSpot().getCode(),
                r.getStatus(), r.getCreatedAt(), r.getExpiresAt());
    }
}
