package com.smartparking.reservation.stats.service;

import com.smartparking.reservation.common.TimeConfig;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import com.smartparking.reservation.stats.dto.DailyStatsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revenue and occupancy per day for the admin dashboard, from this service's own sessions (each one knows its
 * car park, its times, its cost and, from the payment events, whether it was paid). Computed on request: a demo
 * has a few hundred sessions a week, so no summary table is needed.
 */
@Service
@RequiredArgsConstructor
public class DailyStatsService {

	private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

	private final ParkingSessionRepository sessionRepository;
	private final Clock clock;

	/** The last {@code days} days, oldest first; the last one is today, counted up to now. */
	@Transactional(readOnly = true)
	public List<DailyStatsResponse> lastDays(UUID locationId, int days) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, TimeConfig.CAR_PARK_ZONE);
		LocalDate first = today.minusDays(days - 1L);
		List<ParkingSession> sessions = sessionRepository.findOverlapping(locationId, startOf(first), now);

		List<DailyStatsResponse> result = new ArrayList<>(days);
		for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
			result.add(day(date, sessions, now));
		}
		return result;
	}

	private DailyStatsResponse day(LocalDate date, List<ParkingSession> sessions, Instant now) {
		Instant from = startOf(date);
		Instant to = min(startOf(date.plusDays(1)), now);
		int started = 0;
		Duration occupied = Duration.ZERO;
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal unpaid = BigDecimal.ZERO;
		for (ParkingSession session : sessions) {
			if (within(session.getStartedAt(), from, to)) {
				started++;
			}
			// A running session counts up to now.
			Instant end = session.getEndedAt() == null ? now : session.getEndedAt();
			Instant overlapStart = max(session.getStartedAt(), from);
			Instant overlapEnd = min(end, to);
			if (overlapEnd.isAfter(overlapStart)) {
				occupied = occupied.plus(Duration.between(overlapStart, overlapEnd));
			}
			if (session.getEndedAt() != null && within(session.getEndedAt(), from, to)) {
				if (session.getPaymentStatus() == PaymentStatus.COMPLETED) {
					revenue = revenue.add(session.getCost());
				}
				else {
					unpaid = unpaid.add(session.getCost());
				}
			}
		}
		return new DailyStatsResponse(date, hours(Duration.between(from, to)), started, hours(occupied), revenue, unpaid);
	}

	private static Instant startOf(LocalDate date) {
		// Midnight in the car park's zone: a day is 23 or 25 hours long when the clocks change.
		return date.atStartOfDay(TimeConfig.CAR_PARK_ZONE).toInstant();
	}

	private static boolean within(Instant instant, Instant from, Instant to) {
		return !instant.isBefore(from) && instant.isBefore(to);
	}

	private static BigDecimal hours(Duration duration) {
		return BigDecimal.valueOf(duration.toSeconds()).divide(SECONDS_PER_HOUR, 1, RoundingMode.HALF_UP);
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static Instant max(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

}
