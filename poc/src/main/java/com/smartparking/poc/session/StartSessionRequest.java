package com.smartparking.poc.session;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartSessionRequest(@NotNull UUID driverId, @NotNull UUID spotId) {
}
