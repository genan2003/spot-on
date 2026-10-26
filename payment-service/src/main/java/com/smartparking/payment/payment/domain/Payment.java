package com.smartparking.payment.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payment for one completed parking session (the session lives in reservation-service).
 * A failed payment can be retried; every attempt updates this row and counts in {@link #attempts}.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID sessionId;

	private String driverId;

	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status = PaymentStatus.PENDING;

	private int attempts;

	private Instant createdAt;

	private Instant processedAt;

	public Payment(UUID sessionId, String driverId, BigDecimal amount, PaymentMethod method, Instant createdAt) {
		this.sessionId = sessionId;
		this.driverId = driverId;
		this.amount = amount;
		this.method = method;
		this.createdAt = createdAt;
	}

	/** Only a payment that hasn't gone through can be tried (again). */
	public void recordAttempt(boolean success, Instant at) {
		if (status == PaymentStatus.COMPLETED) {
			throw new IllegalStateException("This payment has already gone through.");
		}
		attempts++;
		status = success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
		processedAt = at;
	}

}
