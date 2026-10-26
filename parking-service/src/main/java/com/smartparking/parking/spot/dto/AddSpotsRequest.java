package com.smartparking.parking.spot.dto;

import com.smartparking.parking.spot.domain.SpotType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Adds {@code count} identical spots to one zone of one level. */
public record AddSpotsRequest(
		@Min(-5) @Max(20) int level,
		@NotNull @Pattern(regexp = "[A-Z]{1,2}", message = "must be one or two capital letters") String zone,
		@Min(1) @Max(50) int count,
		@NotNull SpotType type,
		boolean evCharging,
		boolean accessible) {
}
