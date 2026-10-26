package com.smartparking.parking.spot.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Ends the hold with this id (a reservation or session). Used by reservation-service to undo a failed step. */
public record ReleaseSpotRequest(@NotNull UUID holdId) {
}
