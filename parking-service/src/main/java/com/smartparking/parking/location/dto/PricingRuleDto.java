package com.smartparking.parking.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartparking.parking.location.domain.PricingStrategyType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Pricing rule in requests and responses. Only the rates the chosen strategy needs are set. */
public record PricingRuleDto(
		@NotNull PricingStrategyType strategy,
		@PositiveOrZero BigDecimal hourlyRate,
		@PositiveOrZero BigDecimal flatRate,
		@PositiveOrZero BigDecimal firstHourRate,
		@PositiveOrZero BigDecimal laterHourRate,
		@PositiveOrZero BigDecimal dayRate,
		@PositiveOrZero BigDecimal nightWeekendRate) {

	/** Rejects a rule that is missing a rate its strategy needs, e.g. TIERED without laterHourRate. */
	@JsonIgnore
	@AssertTrue(message = "is missing a rate that this pricing strategy needs")
	public boolean isComplete() {
		if (strategy == null) {
			return true; // reported by @NotNull
		}
		return switch (strategy) {
			case HOURLY -> hourlyRate != null;
			case FLAT_RATE -> flatRate != null;
			case TIERED -> firstHourRate != null && laterHourRate != null;
			case NIGHT_WEEKEND -> dayRate != null && nightWeekendRate != null;
		};
	}

}
