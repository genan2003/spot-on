package com.smartparking.payment.payment.dto;

import com.smartparking.payment.payment.domain.PaymentMethod;
import com.smartparking.payment.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID sessionId, String driverId, BigDecimal amount, PaymentMethod method,
		PaymentStatus status, int attempts, Instant createdAt, Instant processedAt) {
}
