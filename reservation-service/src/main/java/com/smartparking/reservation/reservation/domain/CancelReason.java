package com.smartparking.reservation.reservation.domain;

/** Why a reservation was cancelled, so the driver can be told the right thing. */
public enum CancelReason {
	/** The driver cancelled it. */
	DRIVER,
	/** An admin took the spot out of service (spot.closed). */
	SPOT_CLOSED
}
