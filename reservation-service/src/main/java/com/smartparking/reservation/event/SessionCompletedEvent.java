package com.smartparking.reservation.event;

import com.smartparking.reservation.session.domain.ParkingSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code session.completed}. The car has left and the cost is known.
 * Consumers: payment-service (charges it), parking-service (frees the spot), notification-service.
 */
public record SessionCompletedEvent(UUID eventId, Instant occurredAt, UUID sessionId, UUID reservationId, String driverId,
		UUID spotId, String spotCode, UUID locationId, String locationName, Instant startedAt, Instant endedAt,
		BigDecimal cost) implements IntegrationEvent {

	public static SessionCompletedEvent of(ParkingSession s, Instant now) {
		return new SessionCompletedEvent(UUID.randomUUID(), now, s.getId(),
				s.getReservation() == null ? null : s.getReservation().getId(), s.getDriverId(), s.getSpotId(),
				s.getSpotCode(), s.getLocationId(), s.getLocationName(), s.getStartedAt(), s.getEndedAt(), s.getCost());
	}

	@Override
	public String routingKey() {
		return "session.completed";
	}

}
