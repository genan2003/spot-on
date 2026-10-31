package com.smartparking.reservation.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingStrategyTest {

	/** Wednesday 4 March 2026, 10:00 in Cluj-Napoca (UTC+2). */
	private static final Instant WEEKDAY_MORNING = Instant.parse("2026-03-04T08:00:00Z");

	private static final PricingStrategy SIX_LEI_AN_HOUR = new HourlyPricingStrategy(lei("6.00"));

	private static BigDecimal lei(String amount) {
		return new BigDecimal(amount);
	}

	private static Instant after(int minutes) {
		return WEEKDAY_MORNING.plus(Duration.ofMinutes(minutes));
	}

	private static BigDecimal cost(PricingStrategy pricing, Instant from, int minutes) {
		return pricing.calculateCost(from, from.plus(Duration.ofMinutes(minutes)));
	}

	/** Concrete strategies charge for the exact time; rounding is a decorator's job. */
	@Nested
	class Strategies {

		@Test
		void hourlyChargesForTheExactTime() {
			assertThat(cost(SIX_LEI_AN_HOUR, WEEKDAY_MORNING, 90)).isEqualByComparingTo("9.00");
		}

		@Test
		void flatRateIgnoresHowLongTheCarStays() {
			PricingStrategy flat = new FlatRatePricingStrategy(lei("15.00"));
			assertThat(cost(flat, WEEKDAY_MORNING, 5)).isEqualByComparingTo("15.00");
			assertThat(cost(flat, WEEKDAY_MORNING, 600)).isEqualByComparingTo("15.00");
		}

		@Test
		void tieredChargesEachBandUntilTheNextOneStarts() {
			PricingStrategy tiered = new TieredPricingStrategy(
					List.of(new PriceBand(4, lei("2.00")), new PriceBand(0, lei("8.00")), new PriceBand(1, lei("4.00"))));
			assertThat(cost(tiered, WEEKDAY_MORNING, 45)).isEqualByComparingTo("6.00");
			// 1 h × 8 + 3 h × 4 + 1 h × 2
			assertThat(cost(tiered, WEEKDAY_MORNING, 300)).isEqualByComparingTo("22.00");
		}

		@Test
		void tieredNeedsABandFromTheFirstHour() {
			assertThatThrownBy(() -> new TieredPricingStrategy(List.of(new PriceBand(1, lei("4.00")))))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void nightWeekendUsesTheDayRateOnWeekdayDaytime() {
			PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"));
			assertThat(cost(strategy, WEEKDAY_MORNING, 120)).isEqualByComparingTo("12.00");
		}

		@Test
		void nightWeekendChargesEachPartOfTheStayAtItsOwnRate() {
			PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"));
			Instant weekdayEvening = Instant.parse("2026-03-04T17:00:00Z"); // 19:00 local
			// 19:00–20:00 day, 20:00–23:00 night: 6 + 3 × 2
			assertThat(cost(strategy, weekdayEvening, 240)).isEqualByComparingTo("12.00");
		}

		@Test
		void nightWeekendUsesTheLowerRateAllWeekend() {
			PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"));
			Instant saturdayNoon = Instant.parse("2026-03-07T10:00:00Z");
			Instant fridayLate = Instant.parse("2026-03-06T21:00:00Z"); // 23:00 local, runs into Saturday
			assertThat(cost(strategy, saturdayNoon, 120)).isEqualByComparingTo("4.00");
			assertThat(cost(strategy, fridayLate, 120)).isEqualByComparingTo("4.00");
		}

		@Test
		void nightWeekendFollowsTheConfiguredNightHours() {
			PricingStrategy strategy = new NightWeekendPricingStrategy(lei("6.00"), lei("2.00"), LocalTime.of(22, 0), LocalTime.of(6, 30));
			Instant weekdayNine = Instant.parse("2026-03-04T19:00:00Z"); // 21:00 local
			Instant earlyMorning = Instant.parse("2026-03-05T04:00:00Z"); // 06:00 local
			// 21:00–22:00 day, 22:00–23:00 night
			assertThat(cost(strategy, weekdayNine, 120)).isEqualByComparingTo("8.00");
			// 06:00–06:30 night, 06:30–07:00 day
			assertThat(cost(strategy, earlyMorning, 60)).isEqualByComparingTo("4.00");
		}
	}

	/** Each decorator wraps a strategy and changes its result; tested on top of 6 lei an hour. */
	@Nested
	class Decorators {

		@ParameterizedTest(name = "{0} min → {1} lei")
		@CsvSource({"0, 6.00", "1, 6.00", "60, 6.00", "61, 12.00", "130, 18.00"})
		void hourlyBlocksChargeEveryStartedHourWithAMinimumOfOne(int minutes, String expected) {
			PricingStrategy pricing = new BillingIncrementDecorator(SIX_LEI_AN_HOUR, Duration.ofMinutes(60));
			assertThat(cost(pricing, WEEKDAY_MORNING, minutes)).isEqualByComparingTo(expected);
		}

		@Test
		void quarterHourBlocksChargeEveryStartedQuarter() {
			PricingStrategy pricing = new BillingIncrementDecorator(SIX_LEI_AN_HOUR, Duration.ofMinutes(15));
			// 70 min → 75 min
			assertThat(cost(pricing, WEEKDAY_MORNING, 70)).isEqualByComparingTo("7.50");
		}

		@Test
		void surchargeAddsOrTakesOffAPercentage() {
			assertThat(cost(new BaySurchargeDecorator(SIX_LEI_AN_HOUR, 20), WEEKDAY_MORNING, 180)).isEqualByComparingTo("21.60");
			assertThat(cost(new BaySurchargeDecorator(SIX_LEI_AN_HOUR, -50), WEEKDAY_MORNING, 180)).isEqualByComparingTo("9.00");
			assertThat(cost(new BaySurchargeDecorator(SIX_LEI_AN_HOUR, -150), WEEKDAY_MORNING, 180)).isEqualByComparingTo("0.00");
		}

		@Test
		void dailyCapLimitsEachStartedDay() {
			PricingStrategy pricing = new DailyCapDecorator(SIX_LEI_AN_HOUR, lei("40.00"));
			assertThat(cost(pricing, WEEKDAY_MORNING, 120)).isEqualByComparingTo("12.00");
			assertThat(cost(pricing, WEEKDAY_MORNING, 600)).isEqualByComparingTo("40.00");
			// 26 h = 2 started days
			assertThat(cost(pricing, WEEKDAY_MORNING, 26 * 60)).isEqualByComparingTo("80.00");
		}

		@Test
		void gracePeriodMakesAShortStayFree() {
			PricingStrategy pricing = new GracePeriodDecorator(SIX_LEI_AN_HOUR, Duration.ofMinutes(15));
			assertThat(cost(pricing, WEEKDAY_MORNING, 10)).isEqualByComparingTo("0.00");
			assertThat(cost(pricing, WEEKDAY_MORNING, 15)).isEqualByComparingTo("0.00");
			assertThat(cost(pricing, WEEKDAY_MORNING, 30)).isEqualByComparingTo("3.00");
		}
	}

	@Nested
	class Rules {

		@Test
		void aPlainRuleIsTheStrategyInHourlyBlocks() {
			PricingStrategy pricing = PricingRule.hourly(lei("6")).toStrategy();
			assertThat(pricing).isInstanceOf(BillingIncrementDecorator.class);
			assertThat(((PricingDecorator) pricing).inner()).isInstanceOf(HourlyPricingStrategy.class);
		}

		@Test
		void eachStrategyTypeBuildsItsStrategy() {
			assertThat(inner(PricingRule.flatRate(lei("15")).toStrategy())).isInstanceOf(FlatRatePricingStrategy.class);
			assertThat(inner(PricingRule.tiered(lei("8"), lei("4")).toStrategy())).isInstanceOf(TieredPricingStrategy.class);
			assertThat(inner(PricingRule.nightWeekend(lei("6"), lei("2")).toStrategy())).isInstanceOf(NightWeekendPricingStrategy.class);
		}

		@Test
		void extrasWrapTheStrategyFromTheInsideOut() {
			PricingRule rule = PricingRule.hourly(lei("6")).withBillingIncrement(15).withSurcharges(new BaySurcharges(0, 20, 0, 0))
				.withDailyCap(lei("40")).withGracePeriod(10);

			PricingStrategy grace = rule.toStrategy(new Bay(BayType.LARGE, false));

			assertThat(grace).isInstanceOf(GracePeriodDecorator.class);
			PricingStrategy cap = inner(grace);
			assertThat(cap).isInstanceOf(DailyCapDecorator.class);
			PricingStrategy surcharge = inner(cap);
			assertThat(surcharge).isInstanceOf(BaySurchargeDecorator.class);
			PricingStrategy increment = inner(surcharge);
			assertThat(increment).isInstanceOf(BillingIncrementDecorator.class);
			assertThat(inner(increment)).isInstanceOf(HourlyPricingStrategy.class);
		}

		@Test
		void allExtrasTogether() {
			PricingRule rule = PricingRule.hourly(lei("6")).withBillingIncrement(15).withSurcharges(new BaySurcharges(0, 20, 0, 10))
				.withDailyCap(lei("40")).withGracePeriod(10);
			Bay largeWithCharger = new Bay(BayType.LARGE, true);

			assertThat(cost(rule.toStrategy(largeWithCharger), WEEKDAY_MORNING, 8)).isEqualByComparingTo("0.00");
			// 70 min → 75 min = 7.50, +30% = 9.75
			assertThat(cost(rule.toStrategy(largeWithCharger), WEEKDAY_MORNING, 70)).isEqualByComparingTo("9.75");
			// 8 h = 48 +30% = 62.40, capped at 40
			assertThat(cost(rule.toStrategy(largeWithCharger), WEEKDAY_MORNING, 480)).isEqualByComparingTo("40.00");
			// The surcharge only applies to the bays it names.
			assertThat(cost(rule.toStrategy(Bay.STANDARD), WEEKDAY_MORNING, 70)).isEqualByComparingTo("7.50");
		}

		@Test
		void twoBandTieredKeepsTheOldFirstHourRule() {
			PricingStrategy tiered = PricingRule.tiered(lei("8.00"), lei("4.00")).toStrategy();
			assertThat(cost(tiered, WEEKDAY_MORNING, 45)).isEqualByComparingTo("8.00");
			// 3 started hours: 8 + 2 × 4
			assertThat(cost(tiered, WEEKDAY_MORNING, 130)).isEqualByComparingTo("16.00");
		}

		@Test
		void surchargesAddUpPerBay() {
			BaySurcharges surcharges = new BaySurcharges(-10, 20, -50, 15);
			assertThat(surcharges.percentFor(Bay.STANDARD)).isZero();
			assertThat(surcharges.percentFor(new Bay(BayType.COMPACT, false))).isEqualTo(-10);
			assertThat(surcharges.percentFor(new Bay(BayType.LARGE, true))).isEqualTo(35);
			assertThat(surcharges.percentFor(new Bay(BayType.MOTORCYCLE, false))).isEqualTo(-50);
		}

		@Test
		void pricingRuleRejectsAMissingRate() {
			assertThatThrownBy(() -> PricingRule.nightWeekend(lei("6"), null).toStrategy())
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("nightWeekendRate");
		}

		private static PricingStrategy inner(PricingStrategy decorated) {
			return ((PricingDecorator) decorated).inner();
		}
	}
}
