package com.smartparking.reservation.event;

import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code reservation.changed}. The driver moved the reservation to another
 * spot ({@code previousSpotId} set) or extended its hold time ({@code previousSpotId} null).
 * Consumers: parking-service (frees the previous spot), notification-service.
 */
public record ReservationChangedEvent(UUID eventId, Instant occurredAt, UUID reservationId, String driverId, UUID spotId,
		String spotCode, UUID locationId, String locationName, Instant expiresAt, UUID previousSpotId, String previousSpotCode)
		implements IntegrationEvent {

	public static ReservationChangedEvent moved(Reservation r, UUID previousSpotId, String previousSpotCode, Instant now) {
		return new ReservationChangedEvent(UUID.randomUUID(), now, r.getId(), r.getDriverId(), r.getSpotId(), r.getSpotCode(),
				r.getLocationId(), r.getLocationName(), r.getExpiresAt(), previousSpotId, previousSpotCode);
	}

	public static ReservationChangedEvent extended(Reservation r, Instant now) {
		return moved(r, null, null, now);
	}

	@Override
	public String routingKey() {
		return "reservation.changed";
	}

}
