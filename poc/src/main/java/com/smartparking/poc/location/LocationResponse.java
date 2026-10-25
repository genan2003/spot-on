package com.smartparking.poc.location;

import java.util.UUID;

public record LocationResponse(UUID id, String name, String address, double latitude, double longitude, PricingRuleDto pricingRule) {

    static LocationResponse from(ParkingLocation l) {
        return new LocationResponse(l.getId(), l.getName(), l.getAddress(), l.getLatitude(), l.getLongitude(), PricingRuleDto.from(l.getPricingRule()));
    }
}
