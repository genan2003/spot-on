package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.CancelReason;
import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code reservation.cancelled}. The driver cancelled a reservation, or an admin
 * closed its spot ({@code reason} says which). Consumers: parking-service (frees the spot), notification-service.
 */
public record ReservationCancelledEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt, CancelReason reason) implements IntegrationEvent {

	public static ReservationCancelledEvent of(Reservation r, Instant now) {
		return new ReservationCancelledEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt(), r.getCancelReason());
	}

	@Override
	public String routingKey() {
		return "reservation.cancelled";
	}

}
