package com.smartparking.reservation.pricing;

import com.smartparking.reservation.common.TimeConfig;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

/**
 * <b>Strategy pattern: Concrete strategy.</b> A day rate and a lower night-and-weekend rate per hour.
 * Each part of the stay is charged at the rate for the time it falls in, so 19:00 to 23:00 on a weekday
 * with a 20:00 night start is one day hour and three night hours. Times are the car parks' local time.
 */
public record NightWeekendPricingStrategy(BigDecimal dayRate, BigDecimal nightWeekendRate, LocalTime nightStart, LocalTime nightEnd)
		implements PricingStrategy {

	static final ZoneId CAR_PARK_ZONE = TimeConfig.CAR_PARK_ZONE;

	public static final LocalTime DEFAULT_NIGHT_START = LocalTime.of(20, 0);

	public static final LocalTime DEFAULT_NIGHT_END = LocalTime.of(8, 0);

	public NightWeekendPricingStrategy(BigDecimal dayRate, BigDecimal nightWeekendRate) {
		this(dayRate, nightWeekendRate, DEFAULT_NIGHT_START, DEFAULT_NIGHT_END);
	}

	@Override
	public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
		BigDecimal cost = BigDecimal.ZERO;
		ZonedDateTime from = entryTime.atZone(CAR_PARK_ZONE);
		ZonedDateTime exit = exitTime.atZone(CAR_PARK_ZONE);
		// Walk the stay in slices that end where the rate can change: midnight, night start, night end.
		while (from.isBefore(exit)) {
			ZonedDateTime to = nextChange(from);
			if (to.isAfter(exit)) {
				to = exit;
			}
			BigDecimal rate = isOffPeak(from) ? nightWeekendRate : dayRate;
			cost = cost.add(PricingStrategy.forTime(rate, Duration.between(from, to)));
			from = to;
		}
		return PricingStrategy.toLei(cost);
	}

	boolean isOffPeak(ZonedDateTime time) {
		DayOfWeek day = time.getDayOfWeek();
		if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
			return true;
		}
		LocalTime t = time.toLocalTime();
		if (nightStart.equals(nightEnd)) {
			return false;
		}
		return nightStart.isBefore(nightEnd)
				? !t.isBefore(nightStart) && t.isBefore(nightEnd)
				: !t.isBefore(nightStart) || t.isBefore(nightEnd);
	}

	private ZonedDateTime nextChange(ZonedDateTime from) {
		return Stream.of(LocalTime.MIDNIGHT, nightStart, nightEnd)
			.map(time -> nextOccurrence(from, time))
			.min(ZonedDateTime::compareTo)
			.orElseThrow();
	}

	private static ZonedDateTime nextOccurrence(ZonedDateTime from, LocalTime time) {
		ZonedDateTime today = from.toLocalDate().atTime(time).atZone(from.getZone());
		return today.isAfter(from) ? today : from.toLocalDate().plusDays(1).atTime(time).atZone(from.getZone());
	}
}
