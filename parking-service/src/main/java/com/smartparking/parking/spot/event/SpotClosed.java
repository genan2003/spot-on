package com.smartparking.parking.spot.event;

import com.smartparking.parking.spot.domain.ParkingSpot;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code spot.closed}. An admin took a reserved spot out of service, so the
 * reservation holding it can't be used. Sent to RabbitMQ after the commit; reservation-service cancels
 * the reservation and notification-service tells the driver. Only published when a reservation held the
 * spot: a free spot has nobody to tell, and an occupied one can't be closed.
 */
public record SpotClosed(UUID eventId, Instant occurredAt, UUID spotId, String spotCode, UUID locationId, String locationName,
		UUID reservationId, String driverId) {

	/** {@code reservationId} and {@code driverId} are the hold the spot had before it was closed. */
	public static SpotClosed of(ParkingSpot spot, UUID reservationId, String driverId) {
		return new SpotClosed(UUID.randomUUID(), Instant.now(), spot.getId(), spot.getCode(), spot.getLocation().getId(),
				spot.getLocation().getName(), reservationId, driverId);
	}

}
