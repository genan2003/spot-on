package com.smartparking.reservation.reservation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code PATCH /api/v1/reservations/{id}}: the spot to move the reservation to. */
public record MoveReservationRequest(@NotNull UUID spotId) {
}
