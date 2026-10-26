package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/** <b>Strategy pattern: Concrete strategy.</b> One price per visit, however long the stay. */
public record FlatRatePricingStrategy(BigDecimal flatRate) implements PricingStrategy {

	@Override
	public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
		return flatRate;
	}
}
