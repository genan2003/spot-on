package com.smartparking.payment.event;

import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** <b>Observer pattern: Event</b> {@code payment.failed}. The charge was declined; the driver can retry. Consumers: reservation-service (receipt), notification-service. */
public record PaymentFailedEvent(UUID eventId, Instant occurredAt, UUID paymentId, UUID sessionId, String driverId,
		BigDecimal amount, PaymentMethod method, int attempts, String message) implements IntegrationEvent {

	public static PaymentFailedEvent of(Payment p, String message, Instant now) {
		return new PaymentFailedEvent(UUID.randomUUID(), now, p.getId(), p.getSessionId(), p.getDriverId(), p.getAmount(),
				p.getMethod(), p.getAttempts(), message);
	}

	@Override
	public String routingKey() {
		return "payment.failed";
	}

}
