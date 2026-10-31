package com.smartparking.parking.spot.domain;

/**
 * <b>State pattern: State.</b> What a spot may do depends on its current state. Every action
 * is rejected by default; each concrete state overrides only the actions it allows, so the
 * transition table lives in four small classes instead of {@code if}s spread over the services.
 *
 * <pre>
 * from \ action    reserve   occupy    release    markOutOfService  returnToService
 * Available        Reserved  Occupied  -          OutOfService      -
 * Reserved         -         Occupied  Available  OutOfService      -
 * Occupied         -         -         Available  -                 -
 * OutOfService     -         -         -          -                 Available
 * </pre>
 */
public interface SpotState {

	SpotStatus getStatus();

	default void reserve(ParkingSpot spot) {
		throw rejected(spot, "be reserved");
	}

	default void occupy(ParkingSpot spot) {
		throw rejected(spot, "be occupied");
	}

	default void release(ParkingSpot spot) {
		throw rejected(spot, "be released");
	}

	default void markOutOfService(ParkingSpot spot) {
		throw rejected(spot, "be taken out of service");
	}

	default void returnToService(ParkingSpot spot) {
		throw rejected(spot, "return to service");
	}

	private IllegalSpotStateTransitionException rejected(ParkingSpot spot, String action) {
		return new IllegalSpotStateTransitionException(spot.getCode(), getStatus(), action);
	}
}
