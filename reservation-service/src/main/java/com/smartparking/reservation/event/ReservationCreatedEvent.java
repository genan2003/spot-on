package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/** <b>Observer pattern: Event</b> {@code reservation.created}. A driver reserved a spot. Consumers: notification-service. */
public record ReservationCreatedEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt) implements IntegrationEvent {

	public static ReservationCreatedEvent of(Reservation r, Instant now) {
		return new ReservationCreatedEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt());
	}

	@Override
	public String routingKey() {
		return "reservation.created";
	}

}
