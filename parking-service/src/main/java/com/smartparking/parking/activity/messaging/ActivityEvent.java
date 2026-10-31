package com.smartparking.parking.activity.messaging;

import com.smartparking.parking.activity.dto.ActivityItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The fields the activity feed reads from reservation-service's and payment-service's events; other fields in the
 * JSON are ignored. A session's price is {@code cost}, a payment's {@code amount}.
 */
public record ActivityEvent(UUID eventId, Instant occurredAt, String driverId, String spotCode, String previousSpotCode,
		UUID locationId, String locationName, BigDecimal cost, BigDecimal amount, String method, String reason) {

	public ActivityItem toItem(String routingKey) {
		return new ActivityItem(eventId, occurredAt, routingKey, driverId, spotCode, previousSpotCode, locationId,
				locationName, amount != null ? amount : cost, method, reason);
	}

}
