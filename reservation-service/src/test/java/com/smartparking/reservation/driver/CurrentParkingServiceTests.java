package com.smartparking.reservation.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.driver.dto.CurrentParkingResponse;
import com.smartparking.reservation.driver.service.CurrentParkingService;
import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.reservation.domain.CancelReason;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.mapper.ReservationMapper;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.domain.SessionStatus;
import com.smartparking.reservation.session.mapper.SessionMapper;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import com.smartparking.reservation.session.service.ParkingSessionFacade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** Which ended reservation or session "My parking" shows; the cause of feedback 8 was showing an old receipt. */
class CurrentParkingServiceTests {

	private static final Instant NOW = Instant.parse("2026-03-04T12:00:00Z");
	private static final String DRIVER = "driver-1";

	private final ReservationRepository reservations = mock();
	private final ParkingSessionRepository sessions = mock();
	private final CurrentParkingService service = new CurrentParkingService(reservations, sessions, mock(ParkingSessionFacade.class),
			Mappers.getMapper(ReservationMapper.class), Mappers.getMapper(SessionMapper.class), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void aReservationCancelledAfterTheLastReceiptReplacesIt() {
		lastSession(minutesAgo(40), PaymentStatus.COMPLETED);
		Reservation cancelled = lastReservation(minutesAgo(5));

		CurrentParkingResponse current = service.current(DRIVER);

		assertThat(current.lastCompleted()).isNull();
		assertThat(current.lastEndedReservation().id()).isEqualTo(cancelled.getId());
		assertThat(current.lastEndedReservation().status()).isEqualTo(ReservationStatus.CANCELLED);
	}

	@Test
	void aReceiptNewerThanTheLastReservationIsShown() {
		ParkingSession session = lastSession(minutesAgo(5), PaymentStatus.COMPLETED);
		lastReservation(minutesAgo(40));

		CurrentParkingResponse current = service.current(DRIVER);

		assertThat(current.lastCompleted().id()).isEqualTo(session.getId());
		assertThat(current.lastEndedReservation()).isNull();
	}

	@Test
	void anUnpaidReceiptStaysSoTheDriverCanPayAgain() {
		ParkingSession unpaid = lastSession(minutesAgo(300), PaymentStatus.FAILED);
		lastReservation(minutesAgo(5));

		CurrentParkingResponse current = service.current(DRIVER);

		assertThat(current.lastCompleted().id()).isEqualTo(unpaid.getId());
		assertThat(current.lastEndedReservation()).isNull();
	}

	@Test
	void afterTwoHoursNothingEndedIsShown() {
		lastSession(minutesAgo(150), PaymentStatus.COMPLETED);
		lastReservation(minutesAgo(121));

		CurrentParkingResponse current = service.current(DRIVER);

		assertThat(current.lastCompleted()).isNull();
		assertThat(current.lastEndedReservation()).isNull();
	}

	private static Instant minutesAgo(int minutes) {
		return NOW.minus(Duration.ofMinutes(minutes));
	}

	private ParkingSession lastSession(Instant endedAt, PaymentStatus payment) {
		ParkingSession session = new ParkingSession(UUID.randomUUID(), DRIVER, UUID.randomUUID(), "A1", UUID.randomUUID(),
				"Central Garage", Bay.STANDARD, null, endedAt.minus(Duration.ofHours(1)));
		session.end(endedAt, new BigDecimal("6.00"));
		session.recordPayment(UUID.randomUUID(), payment);
		when(sessions.findFirstByDriverIdAndStatusOrderByEndedAtDesc(DRIVER, SessionStatus.COMPLETED)).thenReturn(Optional.of(session));
		return session;
	}

	private Reservation lastReservation(Instant cancelledAt) {
		Reservation reservation = new Reservation(UUID.randomUUID(), DRIVER, UUID.randomUUID(), "B2", UUID.randomUUID(),
				"Central Garage", cancelledAt.minus(Duration.ofMinutes(3)), cancelledAt.plus(Duration.ofMinutes(12)));
		reservation.cancel(CancelReason.DRIVER, cancelledAt);
		when(reservations.findFirstByDriverIdAndStatusInAndEndedAtNotNullOrderByEndedAtDesc(eq(DRIVER), any()))
			.thenReturn(Optional.of(reservation));
		return reservation;
	}

}
