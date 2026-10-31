package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * A location's pricing rule as parking-service returns it: which strategy, with which rates, plus the
 * extras that apply to any strategy. parking-service owns the rule; this service only reads it when it
 * prices a session and turns it into behaviour with {@link #toStrategy(Bay)}.
 */
public record PricingRule(
		PricingStrategyType strategy,
		BigDecimal hourlyRate,
		BigDecimal flatRate,
		List<PriceBand> bands,
		BigDecimal dayRate,
		BigDecimal nightWeekendRate,
		LocalTime nightStart,
		LocalTime nightEnd,
		Integer billingIncrementMinutes,
		Integer gracePeriodMinutes,
		BigDecimal dailyCap,
		BaySurcharges surcharges) {

	/** Every started hour counts unless the rule says otherwise. */
	public static final int DEFAULT_BILLING_INCREMENT_MINUTES = 60;

	public static PricingRule hourly(BigDecimal hourlyRate) {
		return of(PricingStrategyType.HOURLY, hourlyRate, null, null, null, null);
	}

	public static PricingRule flatRate(BigDecimal flatRate) {
		return of(PricingStrategyType.FLAT_RATE, null, flatRate, null, null, null);
	}

	public static PricingRule tiered(List<PriceBand> bands) {
		return of(PricingStrategyType.TIERED, null, null, bands, null, null);
	}

	/** Two bands: a first-hour price, then one price for every later hour. */
	public static PricingRule tiered(BigDecimal firstHourRate, BigDecimal laterHourRate) {
		return tiered(List.of(new PriceBand(0, firstHourRate), new PriceBand(1, laterHourRate)));
	}

	public static PricingRule nightWeekend(BigDecimal dayRate, BigDecimal nightWeekendRate) {
		return of(PricingStrategyType.NIGHT_WEEKEND, null, null, null, dayRate, nightWeekendRate);
	}

	private static PricingRule of(PricingStrategyType type, BigDecimal hourlyRate, BigDecimal flatRate, List<PriceBand> bands,
			BigDecimal dayRate, BigDecimal nightWeekendRate) {
		return new PricingRule(type, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, null, null, null, null, null, null);
	}

	public PricingRule withBillingIncrement(int minutes) {
		return new PricingRule(strategy, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, nightStart, nightEnd, minutes,
				gracePeriodMinutes, dailyCap, surcharges);
	}

	public PricingRule withGracePeriod(int minutes) {
		return new PricingRule(strategy, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, nightStart, nightEnd,
				billingIncrementMinutes, minutes, dailyCap, surcharges);
	}

	public PricingRule withDailyCap(BigDecimal cap) {
		return new PricingRule(strategy, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, nightStart, nightEnd,
				billingIncrementMinutes, gracePeriodMinutes, cap, surcharges);
	}

	public PricingRule withSurcharges(BaySurcharges bySpot) {
		return new PricingRule(strategy, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, nightStart, nightEnd,
				billingIncrementMinutes, gracePeriodMinutes, dailyCap, bySpot);
	}

	public PricingRule withNight(LocalTime start, LocalTime end) {
		return new PricingRule(strategy, hourlyRate, flatRate, bands, dayRate, nightWeekendRate, start, end,
				billingIncrementMinutes, gracePeriodMinutes, dailyCap, surcharges);
	}

	/** The strategy for a standard bay without a charger. */
	public PricingStrategy toStrategy() {
		return toStrategy(Bay.STANDARD);
	}

	/**
	 * The single place where the rule becomes behaviour. The strategy type picks the
	 * {@link PricingStrategy} class (<b>Strategy</b>); the extras wrap it in decorators (<b>Decorator</b>),
	 * always in this order, from the inside out:
	 * <ol>
	 *   <li>{@link BillingIncrementDecorator}: round the stay up to whole blocks (always present),</li>
	 *   <li>{@link BaySurchargeDecorator}: the bay's surcharge or discount, if not zero,</li>
	 *   <li>{@link DailyCapDecorator}: the daily maximum, which includes the surcharge,</li>
	 *   <li>{@link GracePeriodDecorator}: a short enough stay is free, whatever the rest says.</li>
	 * </ol>
	 * Callers only ever see the {@link PricingStrategy} interface.
	 */
	public PricingStrategy toStrategy(Bay bay) {
		PricingStrategy pricing = switch (strategy) {
			case HOURLY -> new HourlyPricingStrategy(required(hourlyRate, "hourlyRate"));
			case FLAT_RATE -> new FlatRatePricingStrategy(required(flatRate, "flatRate"));
			case TIERED -> new TieredPricingStrategy(required(bands, "bands"));
			case NIGHT_WEEKEND -> new NightWeekendPricingStrategy(required(dayRate, "dayRate"),
					required(nightWeekendRate, "nightWeekendRate"),
					Objects.requireNonNullElse(nightStart, NightWeekendPricingStrategy.DEFAULT_NIGHT_START),
					Objects.requireNonNullElse(nightEnd, NightWeekendPricingStrategy.DEFAULT_NIGHT_END));
		};

		int increment = Objects.requireNonNullElse(billingIncrementMinutes, DEFAULT_BILLING_INCREMENT_MINUTES);
		pricing = new BillingIncrementDecorator(pricing, Duration.ofMinutes(increment));

		int surcharge = Objects.requireNonNullElse(surcharges, BaySurcharges.NONE).percentFor(bay);
		if (surcharge != 0) {
			pricing = new BaySurchargeDecorator(pricing, surcharge);
		}
		if (dailyCap != null) {
			pricing = new DailyCapDecorator(pricing, dailyCap);
		}
		if (gracePeriodMinutes != null && gracePeriodMinutes > 0) {
			pricing = new GracePeriodDecorator(pricing, Duration.ofMinutes(gracePeriodMinutes));
		}
		return pricing;
	}

	private <T> T required(T value, String name) {
		return Objects.requireNonNull(value, () -> strategy + " pricing needs " + name);
	}

}
