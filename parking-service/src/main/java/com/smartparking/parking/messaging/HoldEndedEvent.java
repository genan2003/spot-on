package com.smartparking.parking.messaging;

import java.util.UUID;

/**
 * The fields parking-service reads from reservation.cancelled, reservation.expired, reservation.changed
 * and session.completed (published by reservation-service). Other fields in the JSON are ignored.
 * {@code previousSpotId} is only set on reservation.changed when the reservation moved to another spot.
 */
public record HoldEndedEvent(UUID eventId, UUID spotId, UUID reservationId, UUID sessionId, UUID previousSpotId) {

	/** The spot this event frees: the one the reservation moved away from, or the event's spot. */
	public UUID spotToFree(String routingKey) {
		return routingKey.equals("reservation.changed") ? previousSpotId : spotId;
	}
}
