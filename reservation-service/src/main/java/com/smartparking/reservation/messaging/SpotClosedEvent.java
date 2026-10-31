package com.smartparking.reservation.messaging;

import java.util.UUID;

/** The fields reservation-service reads from {@code spot.closed} (published by parking-service). */
public record SpotClosedEvent(UUID eventId, UUID spotId, UUID reservationId) {
}
