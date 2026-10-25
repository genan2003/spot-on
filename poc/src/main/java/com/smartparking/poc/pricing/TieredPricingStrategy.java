package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <b>Strategy pattern: Concrete strategy.</b> The first hour has its own price; every later
 * started hour costs {@code laterHourRate}.
 */
public record TieredPricingStrategy(BigDecimal firstHourRate, BigDecimal laterHourRate) implements PricingStrategy {

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        long laterHours = PricingStrategy.startedHours(entryTime, exitTime) - 1;
        return firstHourRate.add(laterHourRate.multiply(BigDecimal.valueOf(laterHours)));
    }
}
