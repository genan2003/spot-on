package com.smartparking.reservation.client;

/** parking-service refused a spot change (409): the spot isn't free, or another driver holds it. */
public class SpotConflictException extends IllegalStateException {

	public SpotConflictException(String message) {
		super(message);
	}

}
