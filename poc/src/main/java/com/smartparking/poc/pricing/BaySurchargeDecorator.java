package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <b>Decorator pattern: Concrete decorator.</b> Adds a percentage for the kind of bay, e.g. +20% for a
 * large bay with EV charging, or takes it off when negative (a cheaper motorcycle bay).
 * {@link PricingRule#toStrategy} works out the percentage for the spot.
 */
public class BaySurchargeDecorator extends PricingDecorator {

    private final int percent;

    public BaySurchargeDecorator(PricingStrategy inner, int percent) {
        super(inner);
        this.percent = percent;
    }

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        BigDecimal factor = BigDecimal.valueOf(100L + percent).movePointLeft(2);
        return PricingStrategy.toLei(inner.calculateCost(entryTime, exitTime).multiply(factor).max(BigDecimal.ZERO));
    }
}
