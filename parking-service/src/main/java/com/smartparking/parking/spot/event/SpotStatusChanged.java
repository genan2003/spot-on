package com.smartparking.parking.spot.event;

import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event.</b> A spot changed status. Published inside the transaction that
 * changed it, sent to RabbitMQ after the commit as {@code spot.status-changed}, and forwarded to
 * browsers on {@code /topic/spot-status} (the frontend reads spotId, locationId, status, heldBy).
 */
public record SpotStatusChanged(UUID eventId, Instant occurredAt, UUID spotId, UUID locationId, SpotStatus status, String heldBy) {

	public static SpotStatusChanged of(ParkingSpot spot) {
		return new SpotStatusChanged(UUID.randomUUID(), Instant.now(), spot.getId(), spot.getLocation().getId(),
				spot.getStatus(), spot.getHeldBy());
	}

}
