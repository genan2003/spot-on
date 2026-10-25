package com.smartparking.poc.location;

import com.smartparking.poc.spot.SpotType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddSpotsRequest(
        int level,
        @NotNull @Pattern(regexp = "[A-Z]{1,2}") String zone,
        @Min(1) @Max(50) int count,
        @NotNull SpotType type,
        boolean evCharging,
        boolean accessible) {
}
