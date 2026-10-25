package com.smartparking.poc.location;

import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.pricing.PricingStrategyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Pricing rule in requests and responses. Only the rates the chosen strategy needs are set. */
public record PricingRuleDto(
        @NotNull PricingStrategyType strategyType,
        @PositiveOrZero BigDecimal hourlyRate,
        @PositiveOrZero BigDecimal flatRate,
        @PositiveOrZero BigDecimal firstHourRate,
        @PositiveOrZero BigDecimal laterHourRate,
        @PositiveOrZero BigDecimal dayRate,
        @PositiveOrZero BigDecimal nightWeekendRate) {

    static PricingRuleDto from(PricingRule r) {
        return new PricingRuleDto(r.getStrategyType(), r.getHourlyRate(), r.getFlatRate(), r.getFirstHourRate(),
                r.getLaterHourRate(), r.getDayRate(), r.getNightWeekendRate());
    }

    PricingRule toPricingRule() {
        return switch (strategyType) {
            case HOURLY -> PricingRule.hourly(hourlyRate);
            case FLAT_RATE -> PricingRule.flatRate(flatRate);
            case TIERED -> PricingRule.tiered(firstHourRate, laterHourRate);
            case NIGHT_WEEKEND -> PricingRule.nightWeekend(dayRate, nightWeekendRate);
        };
    }
}
