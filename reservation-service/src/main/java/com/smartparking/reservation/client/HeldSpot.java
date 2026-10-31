package com.smartparking.reservation.client;

import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.pricing.BayType;
import java.util.UUID;

/**
 * parking-service's answer to a reserve, occupy or release call. {@code type} and {@code evCharging}
 * describe the bay, so a session can be priced with the bay's surcharge.
 */
public record HeldSpot(UUID spotId, String spotCode, UUID locationId, String locationName, String status, String heldBy,
		BayType type, boolean evCharging) {

	public HeldSpot(UUID spotId, String spotCode, UUID locationId, String locationName, String status, String heldBy) {
		this(spotId, spotCode, locationId, locationName, status, heldBy, BayType.STANDARD, false);
	}

	public Bay bay() {
		return new Bay(type, evCharging);
	}
}
