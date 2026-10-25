package com.smartparking.poc.pricing;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The pricing an administrator configured for a location: which strategy, with which rates.
 * Stored as plain columns on the location; {@link #toStrategy()} turns it into behaviour.
 * Only the rates the chosen strategy needs are set; the rest stay {@code null}.
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
     * The single place where the stored strategy type picks a {@link PricingStrategy} class.
     * Callers only ever see the {@link PricingStrategy} interface.
     */
    public PricingStrategy toStrategy() {
        return switch (strategyType) {
            case HOURLY -> new HourlyPricingStrategy(required(hourlyRate, "hourlyRate"));
            case FLAT_RATE -> new FlatRatePricingStrategy(required(flatRate, "flatRate"));
            case TIERED -> new TieredPricingStrategy(required(firstHourRate, "firstHourRate"), required(laterHourRate, "laterHourRate"));
            case NIGHT_WEEKEND -> new NightWeekendPricingStrategy(required(dayRate, "dayRate"), required(nightWeekendRate, "nightWeekendRate"));
        };
    }

    private BigDecimal required(BigDecimal rate, String name) {
        return Objects.requireNonNull(rate, () -> strategyType + " pricing needs " + name);
    }
}
