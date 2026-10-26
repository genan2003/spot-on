package com.smartparking.reservation.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code reservationId} is optional: a driver can also drive in without reserving. */
public record StartSessionRequest(@NotNull UUID spotId, UUID reservationId, @NotBlank String driverId) {
}
