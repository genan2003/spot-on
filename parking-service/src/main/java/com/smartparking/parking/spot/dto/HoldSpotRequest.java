package com.smartparking.parking.spot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Sent by reservation-service to reserve or occupy a spot. {@code holdId} is the reservation's id
 * (reserve) or the session's id (occupy); a later release must name the same id.
 */
public record HoldSpotRequest(@NotBlank String driverId, @NotNull UUID holdId) {
}
