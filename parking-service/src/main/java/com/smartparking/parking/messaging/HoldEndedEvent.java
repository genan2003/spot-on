package com.smartparking.parking.messaging;

import java.util.UUID;

/**
 * The fields parking-service reads from reservation.cancelled, reservation.expired and
 * session.completed (published by reservation-service). Other fields in the JSON are ignored.
 */
public record HoldEndedEvent(UUID eventId, UUID spotId, UUID reservationId, UUID sessionId) {
}
