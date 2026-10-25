package com.smartparking.poc.reservation;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationRequest(@NotNull UUID driverId, @NotNull UUID spotId) {
}
