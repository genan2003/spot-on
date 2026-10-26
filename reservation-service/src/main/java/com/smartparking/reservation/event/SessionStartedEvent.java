package com.smartparking.reservation.event;

import com.smartparking.reservation.session.domain.ParkingSession;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code session.started}. No service needs it today (the spot is
 * occupied synchronously before the session exists); it is published for completeness and shows up
 * in the RabbitMQ UI.
 */
public record SessionStartedEvent(UUID eventId, Instant occurredAt, UUID sessionId, UUID reservationId, String driverId,
		UUID spotId, String spotCode, UUID locationId, String locationName, Instant startedAt) implements IntegrationEvent {

	public static SessionStartedEvent of(ParkingSession s, Instant now) {
		return new SessionStartedEvent(UUID.randomUUID(), now, s.getId(),
				s.getReservation() == null ? null : s.getReservation().getId(), s.getDriverId(), s.getSpotId(),
				s.getSpotCode(), s.getLocationId(), s.getLocationName(), s.getStartedAt());
	}

	@Override
	public String routingKey() {
		return "session.started";
	}

}
