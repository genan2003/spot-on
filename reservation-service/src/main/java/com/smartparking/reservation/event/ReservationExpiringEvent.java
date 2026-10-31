package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code reservation.expiring}. A reservation's hold ends soon and the driver
 * hasn't started a session. Consumer: notification-service (reminds the driver to start or extend).
 */
public record ReservationExpiringEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt) implements IntegrationEvent {

	public static ReservationExpiringEvent of(Reservation r, Instant now) {
		return new ReservationExpiringEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt());
	}

	@Override
	public String routingKey() {
		return "reservation.expiring";
	}

}
