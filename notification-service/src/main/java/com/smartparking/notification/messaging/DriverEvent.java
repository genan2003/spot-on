package com.smartparking.notification.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The fields notification-service reads from any reservation, session or payment event. Each event
 * fills only some of them (e.g. {@code cost} on session.completed, {@code amount} on payment.*,
 * {@code previousSpotCode} on reservation.changed when the reservation moved);
 * the routing key says which event it is.
 */
public record DriverEvent(UUID eventId, String driverId, String spotCode, String locationName, Instant expiresAt,
		BigDecimal cost, BigDecimal amount, String previousSpotCode) {
}
