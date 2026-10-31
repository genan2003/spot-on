package com.smartparking.parking.spot.event;

import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event.</b> A spot changed status, or an admin changed its properties. Published
 * inside the transaction that changed it, sent to RabbitMQ after the commit as {@code spot.status-changed},
 * and forwarded to browsers on {@code /topic/spot-status}. It carries the spot's live state, so the plan
 * redraws the bay without a reload.
 */
public record SpotStatusChanged(UUID eventId, Instant occurredAt, UUID spotId, UUID locationId, SpotStatus status, String heldBy,
		String code, SpotType type, boolean evCharging, boolean accessible, String licencePlate) {

	public static SpotStatusChanged of(ParkingSpot spot) {
		return new SpotStatusChanged(UUID.randomUUID(), Instant.now(), spot.getId(), spot.getLocation().getId(),
				spot.getStatus(), spot.getHeldBy(), spot.getCode(), spot.getType(), spot.isEvCharging(), spot.isAccessible(),
				spot.getLicencePlate());
	}

}
