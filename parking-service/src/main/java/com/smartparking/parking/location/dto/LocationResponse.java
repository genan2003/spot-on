package com.smartparking.parking.location.dto;

import java.util.UUID;

public record LocationResponse(UUID id, String name, String address, double latitude, double longitude,
		long totalSpots, long freeSpots, PricingRuleDto pricing) {
}
