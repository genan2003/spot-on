package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** <b>Strategy pattern: Concrete strategy.</b> The same price for every hour of the stay. */
public record HourlyPricingStrategy(BigDecimal hourlyRate) implements PricingStrategy {

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        return PricingStrategy.toLei(PricingStrategy.forTime(hourlyRate, Duration.between(entryTime, exitTime)));
    }
}
