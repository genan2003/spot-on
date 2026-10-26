package com.smartparking.parking.spot.dto;

import com.smartparking.parking.spot.domain.SpotStatus;
import jakarta.validation.constraints.NotNull;

/** Admin change: {@code OUT_OF_SERVICE} closes a free spot, {@code AVAILABLE} reopens a closed one. */
public record SpotStatusRequest(@NotNull SpotStatus status) {
}
