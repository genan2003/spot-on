package com.smartparking.poc.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PricingStrategyTest {

    /** Wednesday 4 March 2026, 10:00 in Cluj-Napoca (UTC+2). */
    private static final Instant WEEKDAY_MORNING = Instant.parse("2026-03-04T08:00:00Z");

    private static BigDecimal lei(String amount) {
        return new BigDecimal(amount);
    }

    private static Instant after(int minutes) {
        return WEEKDAY_MORNING.plus(Duration.ofMinutes(minutes));
    }

    @ParameterizedTest(name = "{0} min → {1} started hours")
    @CsvSource({"0, 1", "1, 1", "60, 1", "61, 2", "130, 3", "180, 3"})
    void chargesEveryStartedHourWithAMinimumOfOne(int minutes, long hours) {
        assertThat(PricingStrategy.startedHours(WEEKDAY_MORNING, after(minutes))).isEqualTo(hours);
    }

    @Test
    void hourlyChargesTheSameForEveryStartedHour() {
        assertThat(new HourlyPricingStrategy(lei("6.00")).calculateCost(WEEKDAY_MORNING, after(130))).isEqualByComparingTo("18.00");
    }

    @Test
    void flatRateIgnoresHowLongTheCarStays() {
        PricingStrategy flat = new FlatRatePricingStrategy(lei("15.00"));
        assertThat(flat.calculateCost(WEEKDAY_MORNING, after(5))).isEqualByComparingTo("15.00");
        assertThat(flat.calculateCost(WEEKDAY_MORNING, after(600))).isEqualByComparingTo("15.00");
    }

    @Test
    void tieredChargesTheFirstHourThenTheLaterRate() {
        PricingStrategy tiered = new TieredPricingStrategy(lei("8.00"), lei("4.00"));
        assertThat(tiered.calculateCost(WEEKDAY_MORNING, after(45))).isEqualByComparingTo("8.00");
        assertThat(tiered.calculateCost(WEEKDAY_MORNING, after(130))).isEqualByComparingTo("16.00");
    }

    @Test
    void nightWeekendUsesTheDayRateOnWeekdayDaytime() {
        PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"));
        assertThat(strategy.calculateCost(WEEKDAY_MORNING, after(120))).isEqualByComparingTo("12.00");
    }

    @Test
    void nightWeekendUsesTheLowerRateAtNightAndOnWeekends() {
        PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"));
        Instant weekdayNight = Instant.parse("2026-03-04T20:30:00Z"); // 22:30 local
        Instant saturdayNoon = Instant.parse("2026-03-07T10:00:00Z"); // 12:00 local
        assertThat(strategy.calculateCost(weekdayNight, weekdayNight.plus(Duration.ofHours(2)))).isEqualByComparingTo("4.00");
        assertThat(strategy.calculateCost(saturdayNoon, saturdayNoon.plus(Duration.ofHours(2)))).isEqualByComparingTo("4.00");
    }

    @Test
    void pricingRuleBuildsTheMatchingStrategy() {
        assertThat(PricingRule.hourly(lei("6")).toStrategy()).isInstanceOf(HourlyPricingStrategy.class);
        assertThat(PricingRule.flatRate(lei("15")).toStrategy()).isInstanceOf(FlatRatePricingStrategy.class);
        assertThat(PricingRule.tiered(lei("8"), lei("4")).toStrategy()).isInstanceOf(TieredPricingStrategy.class);
        assertThat(PricingRule.nightWeekend(lei("6"), lei("2")).toStrategy()).isInstanceOf(NightWeekendPricingStrategy.class);
    }

    @Test
    void pricingRuleRejectsAMissingRate() {
        assertThatThrownBy(() -> PricingRule.tiered(lei("8"), null).toStrategy())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("laterHourRate");
    }
}
