package com.smartparking.poc.pricing;

import com.smartparking.poc.spot.SpotType;
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
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The pricing an administrator configured for a location: which strategy, with which rates, plus extras
 * that apply to any strategy (billing blocks, a free grace period, a daily maximum, spot surcharges).
 * Stored as columns on the location (tiered bands in their own table); {@link #toStrategy} turns it into
 * behaviour. Only the rates the chosen strategy needs are set; the rest stay {@code null}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricingRule {

    /** Every started hour counts unless the rule says otherwise. */
    public static final int DEFAULT_BILLING_INCREMENT_MINUTES = 60;

    @Enumerated(EnumType.STRING)
    private PricingStrategyType strategyType;

    private BigDecimal hourlyRate;
    private BigDecimal flatRate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pricing_band", joinColumns = @JoinColumn(name = "location_id"))
    @OrderBy("fromHour")
    private List<PriceBand> bands = new ArrayList<>();

    private BigDecimal dayRate;
    private BigDecimal nightWeekendRate;
    private LocalTime nightStart;
    private LocalTime nightEnd;

    private int billingIncrementMinutes = DEFAULT_BILLING_INCREMENT_MINUTES;
    private int gracePeriodMinutes;
    /** {@code null} = no daily maximum. */
    private BigDecimal dailyCap;

    @Embedded
    private BaySurcharges surcharges = BaySurcharges.none();

    public static PricingRule hourly(BigDecimal hourlyRate) {
        PricingRule rule = new PricingRule();
        rule.strategyType = PricingStrategyType.HOURLY;
        rule.hourlyRate = hourlyRate;
        return rule;
    }

    public static PricingRule flatRate(BigDecimal flatRate) {
        PricingRule rule = new PricingRule();
        rule.strategyType = PricingStrategyType.FLAT_RATE;
        rule.flatRate = flatRate;
        return rule;
    }

    public static PricingRule tiered(List<PriceBand> bands) {
        PricingRule rule = new PricingRule();
        rule.strategyType = PricingStrategyType.TIERED;
        rule.bands = new ArrayList<>(bands);
        return rule;
    }

    /** Two bands: a first-hour price, then one price for every later hour. */
    public static PricingRule tiered(BigDecimal firstHourRate, BigDecimal laterHourRate) {
        return tiered(List.of(new PriceBand(0, firstHourRate), new PriceBand(1, laterHourRate)));
    }

    /** Night hours default to 20:00–08:00. */
    public static PricingRule nightWeekend(BigDecimal dayRate, BigDecimal nightWeekendRate) {
        PricingRule rule = new PricingRule();
        rule.strategyType = PricingStrategyType.NIGHT_WEEKEND;
        rule.dayRate = dayRate;
        rule.nightWeekendRate = nightWeekendRate;
        rule.nightStart = NightWeekendPricingStrategy.DEFAULT_NIGHT_START;
        rule.nightEnd = NightWeekendPricingStrategy.DEFAULT_NIGHT_END;
        return rule;
    }

    public PricingRule withNight(LocalTime start, LocalTime end) {
        PricingRule copy = copy();
        copy.nightStart = start;
        copy.nightEnd = end;
        return copy;
    }

    public PricingRule withBillingIncrement(int minutes) {
        PricingRule copy = copy();
        copy.billingIncrementMinutes = minutes;
        return copy;
    }

    public PricingRule withGracePeriod(int minutes) {
        PricingRule copy = copy();
        copy.gracePeriodMinutes = minutes;
        return copy;
    }

    public PricingRule withDailyCap(BigDecimal cap) {
        PricingRule copy = copy();
        copy.dailyCap = cap;
        return copy;
    }

    public PricingRule withSurcharges(BaySurcharges bySpot) {
        PricingRule copy = copy();
        copy.surcharges = bySpot;
        return copy;
    }

    /** The strategy for a standard spot without a charger. */
    public PricingStrategy toStrategy() {
        return toStrategy(SpotType.STANDARD, false);
    }

    /**
     * The single place where the rule becomes behaviour. The strategy type picks the
     * {@link PricingStrategy} class (<b>Strategy</b>); the extras wrap it in decorators (<b>Decorator</b>),
     * always in this order, from the inside out:
     * <ol>
     *   <li>{@link BillingIncrementDecorator}: round the stay up to whole blocks (always present),</li>
     *   <li>{@link BaySurchargeDecorator}: the spot's surcharge or discount, if not zero,</li>
     *   <li>{@link DailyCapDecorator}: the daily maximum, which includes the surcharge,</li>
     *   <li>{@link GracePeriodDecorator}: a short enough stay is free, whatever the rest says.</li>
     * </ol>
     * Callers only ever see the {@link PricingStrategy} interface.
     */
    public PricingStrategy toStrategy(SpotType spotType, boolean evCharging) {
        PricingStrategy pricing = switch (strategyType) {
            case HOURLY -> new HourlyPricingStrategy(required(hourlyRate, "hourlyRate"));
            case FLAT_RATE -> new FlatRatePricingStrategy(required(flatRate, "flatRate"));
            case TIERED -> new TieredPricingStrategy(required(bands, "bands"));
            case NIGHT_WEEKEND -> new NightWeekendPricingStrategy(required(dayRate, "dayRate"),
                    required(nightWeekendRate, "nightWeekendRate"),
                    Objects.requireNonNullElse(nightStart, NightWeekendPricingStrategy.DEFAULT_NIGHT_START),
                    Objects.requireNonNullElse(nightEnd, NightWeekendPricingStrategy.DEFAULT_NIGHT_END));
        };

        pricing = new BillingIncrementDecorator(pricing, Duration.ofMinutes(billingIncrementMinutes));

        int surcharge = Objects.requireNonNullElse(surcharges, BaySurcharges.none()).percentFor(spotType, evCharging);
        if (surcharge != 0) {
            pricing = new BaySurchargeDecorator(pricing, surcharge);
        }
        if (dailyCap != null) {
            pricing = new DailyCapDecorator(pricing, dailyCap);
        }
        if (gracePeriodMinutes > 0) {
            pricing = new GracePeriodDecorator(pricing, Duration.ofMinutes(gracePeriodMinutes));
        }
        return pricing;
    }

    private PricingRule copy() {
        PricingRule copy = new PricingRule();
        copy.strategyType = strategyType;
        copy.hourlyRate = hourlyRate;
        copy.flatRate = flatRate;
        copy.bands = new ArrayList<>(bands);
        copy.dayRate = dayRate;
        copy.nightWeekendRate = nightWeekendRate;
        copy.nightStart = nightStart;
        copy.nightEnd = nightEnd;
        copy.billingIncrementMinutes = billingIncrementMinutes;
        copy.gracePeriodMinutes = gracePeriodMinutes;
        copy.dailyCap = dailyCap;
        copy.surcharges = surcharges;
        return copy;
    }

    private <T> T required(T value, String name) {
        return Objects.requireNonNull(value, () -> strategyType + " pricing needs " + name);
    }
}
