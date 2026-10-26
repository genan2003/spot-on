package com.smartparking.reservation.pricing;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * <b>Strategy pattern: Concrete strategy.</b> An hourly day rate, and a different hourly rate when
 * the session starts between 20:00 and 08:00 or on a weekend (local time of the car parks).
 */
public record NightWeekendPricingStrategy(BigDecimal dayRate, BigDecimal nightWeekendRate) implements PricingStrategy {

	private static final ZoneId CAR_PARK_ZONE = ZoneId.of("Europe/Bucharest");

	@Override
	public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
		BigDecimal rate = isNightOrWeekend(entryTime) ? nightWeekendRate : dayRate;
		return rate.multiply(BigDecimal.valueOf(PricingStrategy.startedHours(entryTime, exitTime)));
	}

	private static boolean isNightOrWeekend(Instant time) {
		ZonedDateTime local = time.atZone(CAR_PARK_ZONE);
		DayOfWeek day = local.getDayOfWeek();
		int hour = local.getHour();
		return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || hour >= 20 || hour < 8;
	}
}
