package com.smartparking.reservation.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveRequest(@NotNull UUID spotId, @NotBlank String driverId) {
}
