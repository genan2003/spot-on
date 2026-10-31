package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * <b>Strategy pattern: Strategy</b> and <b>Decorator pattern: Component.</b> One way of turning a parking
 * stay into a price. Each location has one strategy (built from the {@link PricingRule} parking-service
 * stores), wrapped in decorators for the rule's extras; the code that ends a session only calls
 * {@link #calculateCost} and never checks which rule or extras are in use.
 * <p>
 * Concrete strategies charge for the exact time between entry and exit. Rounding to whole blocks
 * ("every started hour") is the job of {@link BillingIncrementDecorator}.
 */
public interface PricingStrategy {

	BigDecimal calculateCost(Instant entryTime, Instant exitTime);

	/** {@code hourlyRate} for {@code length} of time, pro rata to the second. */
	static BigDecimal forTime(BigDecimal hourlyRate, Duration length) {
		return hourlyRate.multiply(BigDecimal.valueOf(length.toSeconds())).divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
	}

	/** Money is stored with two decimals. */
	static BigDecimal toLei(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP);
	}
}
