package com.smartparking.parking.location.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartparking.parking.location.domain.PricingStrategyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Pricing rule in requests and responses. Only the rates the chosen strategy needs are set.
 * The extras ({@code billingIncrementMinutes}, {@code gracePeriodMinutes}, {@code dailyCap},
 * {@code surcharges}) apply to every strategy and may be left out in requests: no extras, hourly blocks.
 */
public record PricingRuleDto(
		@NotNull PricingStrategyType strategy,
		@PositiveOrZero BigDecimal hourlyRate,
		@PositiveOrZero BigDecimal flatRate,
		@Size(max = 10) List<@Valid PriceBandDto> bands,
		@PositiveOrZero BigDecimal dayRate,
		@PositiveOrZero BigDecimal nightWeekendRate,
		@JsonFormat(pattern = "HH:mm") LocalTime nightStart,
		@JsonFormat(pattern = "HH:mm") LocalTime nightEnd,
		Integer billingIncrementMinutes,
		@Min(0) @Max(120) Integer gracePeriodMinutes,
		@Positive BigDecimal dailyCap,
		@Valid BaySurchargesDto surcharges) {

	private static final Set<Integer> BILLING_INCREMENTS = Set.of(15, 30, 60);

	public record PriceBandDto(@Min(0) @Max(72) int fromHour, @NotNull @PositiveOrZero BigDecimal hourlyRate) {
	}

	/** Percent per bay type; -100 makes the bay free, 200 triples the price. */
	public record BaySurchargesDto(
			@Min(-100) @Max(200) int compact,
			@Min(-100) @Max(200) int large,
			@Min(-100) @Max(200) int motorcycle,
			@Min(-100) @Max(200) int evCharging) {
	}

	/** Rejects a rule that is missing a rate its strategy needs, e.g. NIGHT_WEEKEND without nightWeekendRate. */
	@JsonIgnore
	@AssertTrue(message = "is missing a rate that this pricing strategy needs")
	public boolean isComplete() {
		if (strategy == null) {
			return true; // reported by @NotNull
		}
		return switch (strategy) {
			case HOURLY -> hourlyRate != null;
			case FLAT_RATE -> flatRate != null;
			case TIERED -> bands != null && !bands.isEmpty();
			case NIGHT_WEEKEND -> dayRate != null && nightWeekendRate != null;
		};
	}

	/** Tiered bands start at hour 0 and each starts later than the one before. */
	@JsonIgnore
	@AssertTrue(message = "must start at hour 0, each band later than the one before")
	public boolean isBandsInOrder() {
		if (strategy != PricingStrategyType.TIERED || bands == null || bands.isEmpty()) {
			return true;
		}
		if (bands.getFirst().fromHour() != 0) {
			return false;
		}
		for (int i = 1; i < bands.size(); i++) {
			if (bands.get(i).fromHour() <= bands.get(i - 1).fromHour()) {
				return false;
			}
		}
		return true;
	}

	@JsonIgnore
	@AssertTrue(message = "must be 15, 30 or 60 minutes")
	public boolean isBillingIncrementSupported() {
		return billingIncrementMinutes == null || BILLING_INCREMENTS.contains(billingIncrementMinutes);
	}

}
