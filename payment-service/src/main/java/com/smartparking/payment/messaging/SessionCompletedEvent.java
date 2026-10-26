package com.smartparking.payment.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/** The fields payment-service reads from session.completed (published by reservation-service). */
public record SessionCompletedEvent(UUID eventId, UUID sessionId, String driverId, BigDecimal cost) {
}
