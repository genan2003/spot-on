package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * <b>Decorator pattern: Concrete decorator.</b> A driver never pays more than {@code dailyCap} for each
 * started 24 hours of the stay.
 */
public class DailyCapDecorator extends PricingDecorator {

	private static final long DAY = Duration.ofDays(1).toSeconds();

	private final BigDecimal dailyCap;

	public DailyCapDecorator(PricingStrategy inner, BigDecimal dailyCap) {
		super(inner);
		this.dailyCap = dailyCap;
	}

	@Override
	public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
		long days = Math.max(1, (Duration.between(entryTime, exitTime).toSeconds() + DAY - 1) / DAY);
		return inner.calculateCost(entryTime, exitTime).min(PricingStrategy.toLei(dailyCap.multiply(BigDecimal.valueOf(days))));
	}
}
