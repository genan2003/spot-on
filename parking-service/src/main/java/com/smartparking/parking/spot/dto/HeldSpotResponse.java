package com.smartparking.parking.spot.dto;

import com.smartparking.parking.spot.domain.SpotStatus;
import java.util.UUID;

/** What reservation-service needs back after a hold: names to show the driver, and the new status. */
public record HeldSpotResponse(UUID spotId, String spotCode, UUID locationId, String locationName, SpotStatus status, String heldBy) {
}
