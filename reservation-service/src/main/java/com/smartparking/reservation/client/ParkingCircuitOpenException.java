package com.smartparking.reservation.client;

import java.time.Duration;
import lombok.Getter;

/**
 * The circuit breaker is open: parking-service failed most recent calls, so this call was not even tried.
 * Mapped to {@code 503} with a {@code Retry-After} header, so the driver knows when to try again.
 */
@Getter
public class ParkingCircuitOpenException extends ParkingServiceUnavailableException {

	/** How long the breaker stays open before it lets test calls through. */
	private final Duration retryAfter;

	public ParkingCircuitOpenException(Duration retryAfter, Throwable cause) {
		super("parking-service circuit breaker is open.", cause);
		this.retryAfter = retryAfter;
	}

}
