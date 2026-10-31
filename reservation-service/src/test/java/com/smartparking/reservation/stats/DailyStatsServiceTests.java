package com.smartparking.reservation.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import com.smartparking.reservation.stats.dto.DailyStatsResponse;
import com.smartparking.reservation.stats.service.DailyStatsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Days follow the car park's clock (Bucharest, UTC+2 in March), not UTC. */
class DailyStatsServiceTests {

	private static final UUID LOCATION = UUID.randomUUID();
	// 12:00 in Bucharest on 4 March.
	private static final Instant NOW = Instant.parse("2026-03-04T10:00:00Z");

	private final ParkingSessionRepository repository = mock(ParkingSessionRepository.class);
	private final DailyStatsService service = new DailyStatsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void splitsSessionsByLocalDayAndCountsTodayUpToNow() {
		// 21:00 to 01:00 local, paid: three hours on the 3rd, one hour and the revenue on the 4th.
		ParkingSession overnight = session("2026-03-03T19:00:00Z");
		overnight.end(Instant.parse("2026-03-03T23:00:00Z"), new BigDecimal("20.00"));
		overnight.recordPayment(UUID.randomUUID(), PaymentStatus.COMPLETED);
		// 08:00 to 08:30 local, card declined: counted as unpaid, not as revenue.
		ParkingSession declined = session("2026-03-04T06:00:00Z");
		declined.end(Instant.parse("2026-03-04T06:30:00Z"), new BigDecimal("6.00"));
		declined.recordPayment(UUID.randomUUID(), PaymentStatus.FAILED);
		// Parked since 09:00 local and still there: three hours so far.
		ParkingSession running = session("2026-03-04T07:00:00Z");
		// From midnight on the 3rd, local time.
		when(repository.findOverlapping(LOCATION, Instant.parse("2026-03-02T22:00:00Z"), NOW))
			.thenReturn(List.of(overnight, declined, running));

		List<DailyStatsResponse> days = service.lastDays(LOCATION, 2);

		assertThat(days).containsExactly(
				new DailyStatsResponse(LocalDate.parse("2026-03-03"), new BigDecimal("24.0"), 1, new BigDecimal("3.0"),
						BigDecimal.ZERO, BigDecimal.ZERO),
				new DailyStatsResponse(LocalDate.parse("2026-03-04"), new BigDecimal("12.0"), 2, new BigDecimal("4.5"),
						new BigDecimal("20.00"), new BigDecimal("6.00")));
	}

	@Test
	void aDayWithoutSessionsIsListedWithZeros() {
		when(repository.findOverlapping(LOCATION, Instant.parse("2026-02-25T22:00:00Z"), NOW)).thenReturn(List.of());

		List<DailyStatsResponse> days = service.lastDays(LOCATION, 7);

		assertThat(days).hasSize(7).allMatch(day -> day.sessions() == 0 && day.occupiedHours().signum() == 0);
		assertThat(days.getFirst().date()).isEqualTo(LocalDate.parse("2026-02-26"));
		assertThat(days.getLast().date()).isEqualTo(LocalDate.parse("2026-03-04"));
	}

	private static ParkingSession session(String startedAt) {
		return new ParkingSession(UUID.randomUUID(), "driver-1", UUID.randomUUID(), "A1", LOCATION, "Central Garage",
				Bay.STANDARD, "CJ 07 ABC", null, Instant.parse(startedAt));
	}

}
