package com.smartparking.parking.spot.domain;

/** Stored in the database; the matching {@link SpotState} object is rebuilt from it on load. */
public enum SpotStatus {
	AVAILABLE,
	RESERVED,
	OCCUPIED,
	OUT_OF_SERVICE
}
