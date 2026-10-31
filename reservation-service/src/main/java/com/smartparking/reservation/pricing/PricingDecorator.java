package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <b>Decorator pattern: Decorator.</b> A pricing strategy that wraps another one and changes its result:
 * a free grace period, a daily maximum, a surcharge, rounding to whole blocks. Decorators implement the
 * same {@link PricingStrategy} interface, so they stack in any order and callers can't tell them apart
 * from a plain strategy. {@link PricingRule#toStrategy} decides which ones a location uses.
 */
public abstract class PricingDecorator implements PricingStrategy {

	protected final PricingStrategy inner;

	protected PricingDecorator(PricingStrategy inner) {
		this.inner = inner;
	}

	@Override
	public abstract BigDecimal calculateCost(Instant entryTime, Instant exitTime);

	public PricingStrategy inner() {
		return inner;
	}
}
