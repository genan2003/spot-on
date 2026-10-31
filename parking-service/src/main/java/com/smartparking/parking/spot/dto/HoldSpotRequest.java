package com.smartparking.parking.spot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Sent by reservation-service to reserve or occupy a spot. {@code holdId} is the reservation's id
 * (reserve) or the session's id (occupy); a later release must name the same id. {@code licencePlate} is sent
 * with occupy only (reservation-service has already checked and normalised it).
 */
public record HoldSpotRequest(@NotBlank String driverId, @NotNull UUID holdId, @Size(max = 12) String licencePlate) {
}
