package com.smartparking.reservation.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day at one car park, in the car park's local time. {@code hours} is the length of the day counted so far
 * (24, or the hours since midnight for today), so occupancy = {@code occupiedHours} / (spots × {@code hours}).
 *
 * @param sessions sessions that started that day
 * @param occupiedHours spot-hours used that day, by running and ended sessions
 * @param revenue paid cost of the sessions that ended that day
 * @param unpaid cost of the sessions that ended that day and aren't paid yet (failed or still processing)
 */
public record DailyStatsResponse(LocalDate date, BigDecimal hours, int sessions, BigDecimal occupiedHours,
		BigDecimal revenue, BigDecimal unpaid) {
}
