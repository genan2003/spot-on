package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/** <b>Observer pattern: Event</b> {@code reservation.cancelled}. A driver cancelled a reservation. Consumers: parking-service (frees the spot), notification-service. */
public record ReservationCancelledEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt) implements IntegrationEvent {

	public static ReservationCancelledEvent of(Reservation r, Instant now) {
		return new ReservationCancelledEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt());
	}

	@Override
	public String routingKey() {
		return "reservation.cancelled";
	}

}
