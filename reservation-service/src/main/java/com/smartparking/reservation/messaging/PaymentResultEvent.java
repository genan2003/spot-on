package com.smartparking.reservation.messaging;

import java.util.UUID;

/** The fields this service reads from payment.completed and payment.failed (published by payment-service). */
public record PaymentResultEvent(UUID eventId, UUID paymentId, UUID sessionId) {
}
