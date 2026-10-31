package com.smartparking.parking.location.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <b>Strategy pattern: configuration of the Context.</b> The pricing an administrator chose for a
 * location: which strategy, with which rates, plus extras for any strategy (billing blocks, a free grace
 * period, a daily maximum, bay surcharges), which reservation-service applies as <b>decorators</b>.
 * parking-service owns and stores the rule; reservation-service fetches it when it prices a session.
 * Only the rates the chosen strategy needs are kept; the rest are {@code null}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricingRule {

	public static final LocalTime DEFAULT_NIGHT_START = LocalTime.of(20, 0);

	public static final LocalTime DEFAULT_NIGHT_END = LocalTime.of(8, 0);

	public static final int DEFAULT_BILLING_INCREMENT_MINUTES = 60;

	@Enumerated(EnumType.STRING)
	private PricingStrategyType strategyType;

	private BigDecimal hourlyRate;

	private BigDecimal flatRate;

	/** Tiered pricing's bands, in their own table; empty for the other strategies. */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "pricing_band", joinColumns = @JoinColumn(name = "location_id"))
	@OrderBy("fromHour")
	private List<PriceBand> bands = new ArrayList<>();

	private BigDecimal dayRate;

	private BigDecimal nightWeekendRate;

	private LocalTime nightStart;

	private LocalTime nightEnd;

	private int billingIncrementMinutes;

	private int gracePeriodMinutes;

	/** {@code null} = no daily maximum. */
	private BigDecimal dailyCap;

	@Embedded
	private BaySurcharges surcharges;

	/** New locations start at 5 lei per started hour, with no extras, until the admin sets their own rule. */
	public static PricingRule defaultRule() {
		return of(PricingStrategyType.HOURLY, new BigDecimal("5.00"), null, List.of(), null, null, null, null,
				DEFAULT_BILLING_INCREMENT_MINUTES, 0, null, BaySurcharges.none());
	}

	/**
	 * Keeps only the rates the strategy uses, so a stale rate from a previous rule can't linger.
	 * Missing night hours fall back to 20:00–08:00.
	 */
	public static PricingRule of(PricingStrategyType type, BigDecimal hourlyRate, BigDecimal flatRate, List<PriceBand> bands,
			BigDecimal dayRate, BigDecimal nightWeekendRate, LocalTime nightStart, LocalTime nightEnd,
			int billingIncrementMinutes, int gracePeriodMinutes, BigDecimal dailyCap, BaySurcharges surcharges) {
		PricingRule rule = new PricingRule();
		rule.strategyType = type;
		switch (type) {
			case HOURLY -> rule.hourlyRate = hourlyRate;
			case FLAT_RATE -> rule.flatRate = flatRate;
			case TIERED -> rule.bands = new ArrayList<>(bands);
			case NIGHT_WEEKEND -> {
				rule.dayRate = dayRate;
				rule.nightWeekendRate = nightWeekendRate;
				rule.nightStart = Objects.requireNonNullElse(nightStart, DEFAULT_NIGHT_START);
				rule.nightEnd = Objects.requireNonNullElse(nightEnd, DEFAULT_NIGHT_END);
			}
		}
		rule.billingIncrementMinutes = billingIncrementMinutes;
		rule.gracePeriodMinutes = gracePeriodMinutes;
		rule.dailyCap = dailyCap;
		rule.surcharges = surcharges;
		return rule;
	}

}
