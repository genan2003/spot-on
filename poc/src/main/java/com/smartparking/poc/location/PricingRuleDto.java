package com.smartparking.poc.location;

import com.smartparking.poc.pricing.BaySurcharges;
import com.smartparking.poc.pricing.PriceBand;
import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.pricing.PricingStrategyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Pricing rule in requests and responses. Only the rates the chosen strategy needs are set; the extras
 * (billing blocks, grace period, daily cap, surcharges) apply to every strategy and may be left out.
 */
public record PricingRuleDto(
        @NotNull PricingStrategyType strategyType,
        @PositiveOrZero BigDecimal hourlyRate,
        @PositiveOrZero BigDecimal flatRate,
        List<Band> bands,
        @PositiveOrZero BigDecimal dayRate,
        @PositiveOrZero BigDecimal nightWeekendRate,
        LocalTime nightStart,
        LocalTime nightEnd,
        Integer billingIncrementMinutes,
        @Min(0) @Max(120) Integer gracePeriodMinutes,
        @Positive BigDecimal dailyCap,
        Surcharges surcharges) {

    public record Band(int fromHour, BigDecimal hourlyRate) {
    }

    public record Surcharges(int compact, int large, int motorcycle, int evCharging) {
    }

    static PricingRuleDto from(PricingRule r) {
        List<Band> bands = r.getBands().isEmpty() ? null
                : r.getBands().stream().map(b -> new Band(b.fromHour(), b.hourlyRate())).toList();
        BaySurcharges s = r.getSurcharges();
        return new PricingRuleDto(r.getStrategyType(), r.getHourlyRate(), r.getFlatRate(), bands, r.getDayRate(),
                r.getNightWeekendRate(), r.getNightStart(), r.getNightEnd(), r.getBillingIncrementMinutes(),
                r.getGracePeriodMinutes(), r.getDailyCap(),
                new Surcharges(s.getCompact(), s.getLarge(), s.getMotorcycle(), s.getEvCharging()));
    }

    PricingRule toPricingRule() {
        PricingRule rule = switch (strategyType) {
            case HOURLY -> PricingRule.hourly(hourlyRate);
            case FLAT_RATE -> PricingRule.flatRate(flatRate);
            case TIERED -> PricingRule.tiered(bands == null ? List.of()
                    : bands.stream().map(b -> new PriceBand(b.fromHour(), b.hourlyRate())).toList());
            case NIGHT_WEEKEND -> {
                PricingRule night = PricingRule.nightWeekend(dayRate, nightWeekendRate);
                yield nightStart != null && nightEnd != null ? night.withNight(nightStart, nightEnd) : night;
            }
        };
        rule = rule.withBillingIncrement(Objects.requireNonNullElse(billingIncrementMinutes, PricingRule.DEFAULT_BILLING_INCREMENT_MINUTES))
                .withGracePeriod(Objects.requireNonNullElse(gracePeriodMinutes, 0))
                .withDailyCap(dailyCap);
        if (surcharges != null) {
            rule = rule.withSurcharges(new BaySurcharges(surcharges.compact(), surcharges.large(), surcharges.motorcycle(),
                    surcharges.evCharging()));
        }
        return rule;
    }
}
