package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/** <b>Strategy pattern: Concrete strategy.</b> Every started hour costs the same. */
public record HourlyPricingStrategy(BigDecimal hourlyRate) implements PricingStrategy {

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        return hourlyRate.multiply(BigDecimal.valueOf(PricingStrategy.startedHours(entryTime, exitTime)));
    }
}
