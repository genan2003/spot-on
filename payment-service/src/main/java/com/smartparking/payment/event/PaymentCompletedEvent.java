package com.smartparking.payment.event;

import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** <b>Observer pattern: Event</b> {@code payment.completed}. The driver was charged. Consumers: reservation-service (receipt), notification-service. */
public record PaymentCompletedEvent(UUID eventId, Instant occurredAt, UUID paymentId, UUID sessionId, String driverId,
		BigDecimal amount, PaymentMethod method, int attempts, String message) implements IntegrationEvent {

	public static PaymentCompletedEvent of(Payment p, String message, Instant now) {
		return new PaymentCompletedEvent(UUID.randomUUID(), now, p.getId(), p.getSessionId(), p.getDriverId(), p.getAmount(),
				p.getMethod(), p.getAttempts(), message);
	}

	@Override
	public String routingKey() {
		return "payment.completed";
	}

}
