package com.smartparking.parking.location.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.UUID;

/** {@code opensAt} and {@code closesAt} are both {@code null} for a car park that is always open. */
public record LocationResponse(UUID id, String name, String address, double latitude, double longitude,
		long totalSpots, long freeSpots, PricingRuleDto pricing, @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
		@JsonFormat(pattern = "HH:mm") LocalTime closesAt) {
}
