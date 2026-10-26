package com.smartparking.parking.spot.domain;

/**
 * <b>State pattern:</b> thrown when an action is not allowed in the spot's current state,
 * for example reserving a spot that is occupied. It is an {@link IllegalStateException}, so the
 * error handler answers {@code 409 Conflict}.
 */
public class IllegalSpotStateTransitionException extends IllegalStateException {

	public IllegalSpotStateTransitionException(String spotCode, SpotStatus status, String action) {
		super("Spot %s is %s, so it can't %s.".formatted(spotCode, describe(status), action));
	}

	public IllegalSpotStateTransitionException(String message) {
		super(message);
	}

	private static String describe(SpotStatus status) {
		return status.name().toLowerCase().replace('_', ' ');
	}
}
