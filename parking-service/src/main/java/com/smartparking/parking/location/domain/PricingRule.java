package com.smartparking.parking.location.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <b>Strategy pattern: configuration of the Context.</b> The pricing an administrator chose for a
 * location: which strategy, with which rates. parking-service owns and stores it; reservation-service
 * fetches it when a session ends and turns it into a {@code PricingStrategy}.
 * Only the rates the chosen strategy needs are kept; the rest are {@code null}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PricingRule {

	@Enumerated(EnumType.STRING)
	private PricingStrategyType strategyType;

	private BigDecimal hourlyRate;

	private BigDecimal flatRate;

	private BigDecimal firstHourRate;

	private BigDecimal laterHourRate;

	private BigDecimal dayRate;

	private BigDecimal nightWeekendRate;

	/** New locations start at 5 lei per started hour until the admin sets their own rule. */
	public static PricingRule defaultRule() {
		return of(PricingStrategyType.HOURLY, new BigDecimal("5.00"), null, null, null, null, null);
	}

	/** Keeps only the rates the strategy uses, so a stale rate from a previous rule can't linger. */
	public static PricingRule of(PricingStrategyType type, BigDecimal hourlyRate, BigDecimal flatRate,
			BigDecimal firstHourRate, BigDecimal laterHourRate, BigDecimal dayRate, BigDecimal nightWeekendRate) {
		return switch (type) {
			case HOURLY -> new PricingRule(type, hourlyRate, null, null, null, null, null);
			case FLAT_RATE -> new PricingRule(type, null, flatRate, null, null, null, null);
			case TIERED -> new PricingRule(type, null, null, firstHourRate, laterHourRate, null, null);
			case NIGHT_WEEKEND -> new PricingRule(type, null, null, null, null, dayRate, nightWeekendRate);
		};
	}

}
