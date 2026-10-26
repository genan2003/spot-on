package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A location's pricing rule as parking-service returns it: which strategy, with which rates.
 * parking-service owns the rule; this service only reads it when a session ends and turns it into
 * behaviour with {@link #toStrategy()}.
 */
public record PricingRule(
		PricingStrategyType strategy,
		BigDecimal hourlyRate,
		BigDecimal flatRate,
		BigDecimal firstHourRate,
		BigDecimal laterHourRate,
		BigDecimal dayRate,
		BigDecimal nightWeekendRate) {

	public static PricingRule hourly(BigDecimal hourlyRate) {
		return new PricingRule(PricingStrategyType.HOURLY, hourlyRate, null, null, null, null, null);
	}

	public static PricingRule flatRate(BigDecimal flatRate) {
		return new PricingRule(PricingStrategyType.FLAT_RATE, null, flatRate, null, null, null, null);
	}

	public static PricingRule tiered(BigDecimal firstHourRate, BigDecimal laterHourRate) {
		return new PricingRule(PricingStrategyType.TIERED, null, null, firstHourRate, laterHourRate, null, null);
	}

	public static PricingRule nightWeekend(BigDecimal dayRate, BigDecimal nightWeekendRate) {
		return new PricingRule(PricingStrategyType.NIGHT_WEEKEND, null, null, null, null, dayRate, nightWeekendRate);
	}

	/**
	 * The single place where the strategy type picks a {@link PricingStrategy} class.
	 * Callers only ever see the {@link PricingStrategy} interface.
	 */
	public PricingStrategy toStrategy() {
		return switch (strategy) {
			case HOURLY -> new HourlyPricingStrategy(required(hourlyRate, "hourlyRate"));
			case FLAT_RATE -> new FlatRatePricingStrategy(required(flatRate, "flatRate"));
			case TIERED -> new TieredPricingStrategy(required(firstHourRate, "firstHourRate"), required(laterHourRate, "laterHourRate"));
			case NIGHT_WEEKEND -> new NightWeekendPricingStrategy(required(dayRate, "dayRate"), required(nightWeekendRate, "nightWeekendRate"));
		};
	}

	private BigDecimal required(BigDecimal rate, String name) {
		return Objects.requireNonNull(rate, () -> strategy + " pricing needs " + name);
	}

}
