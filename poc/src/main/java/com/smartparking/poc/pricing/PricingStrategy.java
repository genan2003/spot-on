package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * <b>Strategy pattern: Strategy.</b> One way of turning a parking stay into a price.
 * Each location holds one strategy (built from its {@link PricingRule}); the code that ends a
 * session only calls {@link #calculateCost} and never checks which rule is in use.
 */
public interface PricingStrategy {

    BigDecimal calculateCost(Instant entryTime, Instant exitTime);

    /** Every started hour is charged, with a minimum of one hour. */
    static long startedHours(Instant entryTime, Instant exitTime) {
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        return Math.max(1, (minutes + 59) / 60);
    }
}
