package com.smartparking.reservation.reservation.domain;

import java.util.List;

public enum ReservationStatus {
	/** Reserved in the schema for a hold that is not yet confirmed; reservations are created ACTIVE today. */
	PENDING,
	ACTIVE,
	CANCELLED,
	EXPIRED,
	/** The driver started a session on the reserved spot. */
	COMPLETED;

	/** Statuses covered by the unique indexes: a spot or a driver has at most one of these. */
	public static final List<ReservationStatus> OPEN = List.of(PENDING, ACTIVE);
}
