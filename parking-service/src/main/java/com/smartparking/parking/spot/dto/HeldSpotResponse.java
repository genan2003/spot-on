package com.smartparking.parking.spot.dto;

import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import java.util.UUID;

/**
 * What reservation-service needs back after a hold: names to show the driver, the new status, and the
 * bay's type and charger (the pricing rule's surcharge depends on them).
 */
public record HeldSpotResponse(UUID spotId, String spotCode, UUID locationId, String locationName, SpotStatus status, String heldBy,
		SpotType type, boolean evCharging) {
}
