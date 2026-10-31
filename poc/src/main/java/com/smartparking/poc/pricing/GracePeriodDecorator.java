package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * <b>Decorator pattern: Concrete decorator.</b> A driver who leaves within the grace period pays nothing;
 * a longer stay is charged in full by the wrapped strategy.
 */
public class GracePeriodDecorator extends PricingDecorator {

    private final Duration gracePeriod;

    public GracePeriodDecorator(PricingStrategy inner, Duration gracePeriod) {
        super(inner);
        this.gracePeriod = gracePeriod;
    }

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        if (Duration.between(entryTime, exitTime).compareTo(gracePeriod) <= 0) {
            return PricingStrategy.toLei(BigDecimal.ZERO);
        }
        return inner.calculateCost(entryTime, exitTime);
    }
}
