package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/** <b>Observer pattern: Event</b> {@code reservation.expired}. A reservation ran out unused. Consumers: parking-service (frees the spot), notification-service. */
public record ReservationExpiredEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt) implements IntegrationEvent {

	public static ReservationExpiredEvent of(Reservation r, Instant now) {
		return new ReservationExpiredEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt());
	}

	@Override
	public String routingKey() {
		return "reservation.expired";
	}

}
