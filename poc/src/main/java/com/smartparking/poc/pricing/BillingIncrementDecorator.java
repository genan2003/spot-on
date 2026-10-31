package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * <b>Decorator pattern: Concrete decorator.</b> Charges whole blocks: the stay is rounded up to the next
 * multiple of {@code increment} (at least one block) before the wrapped strategy prices it.
 * With 60 minutes this is "every started hour counts".
 */
public class BillingIncrementDecorator extends PricingDecorator {

    private final Duration increment;

    public BillingIncrementDecorator(PricingStrategy inner, Duration increment) {
        super(inner);
        this.increment = increment;
    }

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        long stayed = Duration.between(entryTime, exitTime).toSeconds();
        long block = increment.toSeconds();
        long blocks = Math.max(1, (stayed + block - 1) / block);
        return inner.calculateCost(entryTime, entryTime.plus(increment.multipliedBy(blocks)));
    }
}
