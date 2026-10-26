package com.smartparking.reservation.session.domain;

/** This service's copy of the payment outcome, filled from payment-service's events. */
public enum PaymentStatus {
	PENDING,
	COMPLETED,
	FAILED
}
