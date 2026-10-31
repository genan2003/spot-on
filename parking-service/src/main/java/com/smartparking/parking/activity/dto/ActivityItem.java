package com.smartparking.parking.activity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One reservation, session or payment event as the admin's activity feed shows it. {@code type} is the routing key
 * ({@code reservation.created}, {@code payment.failed}, ...); the other fields are whatever that event carries
 * ({@code null} otherwise): payments name no spot, only sessions and payments have an amount.
 */
public record ActivityItem(UUID eventId, Instant occurredAt, String type, String driverId, String spotCode,
		String previousSpotCode, UUID locationId, String locationName, BigDecimal amount, String method, String reason) {
}
