package com.smartparking.reservation.client;

import com.smartparking.reservation.pricing.PricingRule;
import java.util.UUID;

/** The part of parking-service's location response this service reads. Unknown fields are ignored. */
public record LocationInfo(UUID id, String name, PricingRule pricing) {
}
