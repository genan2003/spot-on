package com.smartparking.reservation.client;

import java.util.UUID;

/** parking-service's answer to a reserve, occupy or release call. */
public record HeldSpot(UUID spotId, String spotCode, UUID locationId, String locationName, String status, String heldBy) {
}
