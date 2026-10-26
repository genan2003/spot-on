package com.smartparking.reservation.client;

/** parking-service didn't answer in time or failed. Mapped to {@code 503 Service Unavailable}. */
public class ParkingServiceUnavailableException extends RuntimeException {

	public ParkingServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
