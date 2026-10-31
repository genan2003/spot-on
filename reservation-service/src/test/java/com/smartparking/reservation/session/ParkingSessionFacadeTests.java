package com.smartparking.reservation.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.LocationInfo;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.client.SpotConflictException;
import com.smartparking.reservation.event.SessionCompletedEvent;
import com.smartparking.reservation.event.SessionStartedEvent;
import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.pricing.BaySurcharges;
import com.smartparking.reservation.pricing.BayType;
import com.smartparking.reservation.pricing.PricingRule;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.domain.SessionStatus;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The facade's orchestration with every subsystem mocked. */
class ParkingSessionFacadeTests {

	/** Wednesday 4 March 2026, 10:00 in Cluj-Napoca: weekday daytime. */
	private static final Instant ENTRY = Instant.parse("2026-03-04T08:00:00Z");
	private static final Instant EXIT = ENTRY.plus(Duration.ofMinutes(130));
	private static final UUID SPOT = UUID.randomUUID();
	private static final UUID LOCATION = UUID.randomUUID();

	private final ParkingSessionRepository sessionRepository = mock();
	private final ReservationRepository reservationRepository = mock();
	private final ParkingClient parkingClient = mock();
	private final ApplicationEventPublisher eventPublisher = mock();
	private final ParkingSessionFacade facade = new ParkingSessionFacade(sessionRepository, reservationRepository,
			parkingClient, new TransactionTemplate(mock(PlatformTransactionManager.class)), eventPublisher,
			Clock.fixed(EXIT, ZoneOffset.UTC));

	@Test
	void endingASessionChargesWithTheLocationsStrategyAndAnnouncesIt() {
		ParkingSession session = activeSession();
		when(parkingClient.getLocation(LOCATION))
			.thenReturn(new LocationInfo(LOCATION, "Central Garage", PricingRule.tiered(new BigDecimal("8.00"), new BigDecimal("4.00"))));

		facade.endSession(session.getId());

		// 130 minutes = 3 started hours: 8 + 2 × 4
		assertThat(session.getCost()).isEqualByComparingTo("16.00");
		assertThat(session.getEndedAt()).isEqualTo(EXIT);
		assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
		assertThat(session.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		verify(sessionRepository).save(session);

		// Payment, spot release and notification are left to the event's consumers.
		ArgumentCaptor<SessionCompletedEvent> event = ArgumentCaptor.forClass(SessionCompletedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
		assertThat(event.getValue().spotId()).isEqualTo(SPOT);
		assertThat(event.getValue().cost()).isEqualByComparingTo("16.00");
		assertThat(event.getValue().routingKey()).isEqualTo("session.completed");
		verify(parkingClient, never()).releaseSpotIfHeld(any(), any());
	}

	@Test
	void theBaysSurchargeIsAddedByTheRulesDecorators() {
		ParkingSession session = activeSession(new Bay(BayType.LARGE, true));
		PricingRule rule = PricingRule.hourly(new BigDecimal("6.00")).withSurcharges(new BaySurcharges(0, 20, 0, 10));
		when(parkingClient.getLocation(LOCATION)).thenReturn(new LocationInfo(LOCATION, "Central Garage", rule));

		facade.endSession(session.getId());

		// 3 started hours × 6 lei = 18, +20% large +10% EV = 23.40
		assertThat(session.getCost()).isEqualByComparingTo("23.40");
	}

	@Test
	void aSessionCannotEndTwice() {
		ParkingSession session = activeSession();
		session.end(EXIT, BigDecimal.ONE);

		assertThatThrownBy(() -> facade.endSession(session.getId())).isInstanceOf(IllegalStateException.class);
		verify(parkingClient, never()).getLocation(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void startingOnASpotThatIsNotFreeSavesNothing() {
		when(parkingClient.occupySpot(eq(SPOT), eq("driver-1"), any(), eq("CJ 07 ABC")))
			.thenThrow(new SpotConflictException("Spot A1 is occupied, so it can't be occupied."));

		assertThatThrownBy(() -> facade.startSession("driver-1", SPOT, null, " cj 07  abc ")).isInstanceOf(SpotConflictException.class);
		verify(sessionRepository, never()).saveAndFlush(any());
	}

	@Test
	void startingOnAFreeSpotRecordsTheEntryTimeWithTheSessionAsTheHold() {
		ArgumentCaptor<UUID> hold = ArgumentCaptor.forClass(UUID.class);
		when(parkingClient.occupySpot(eq(SPOT), eq("driver-1"), hold.capture(), eq("CJ 07 ABC")))
			.thenReturn(new HeldSpot(SPOT, "A1", LOCATION, "Central Garage", "OCCUPIED", "driver-1", BayType.MOTORCYCLE, false));
		when(sessionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

		ParkingSession session = facade.startSession("driver-1", SPOT, null, " cj 07  abc ");

		assertThat(session.getId()).isEqualTo(hold.getValue());
		assertThat(session.getStartedAt()).isEqualTo(EXIT);
		assertThat(session.getLocationName()).isEqualTo("Central Garage");
		assertThat(session.getBay()).isEqualTo(new Bay(BayType.MOTORCYCLE, false));
		assertThat(session.getLicencePlate()).isEqualTo("CJ 07 ABC");
		verify(eventPublisher).publishEvent(any(SessionStartedEvent.class));
	}

	@Test
	void aFailedSaveGivesTheSpotBack() {
		ArgumentCaptor<UUID> hold = ArgumentCaptor.forClass(UUID.class);
		when(parkingClient.occupySpot(eq(SPOT), eq("driver-1"), hold.capture(), eq("CJ 07 ABC")))
			.thenReturn(new HeldSpot(SPOT, "A1", LOCATION, "Central Garage", "OCCUPIED", "driver-1"));
		when(sessionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_session_active_per_driver"));

		assertThatThrownBy(() -> facade.startSession("driver-1", SPOT, null, " cj 07  abc ")).isInstanceOf(DataIntegrityViolationException.class);
		verify(parkingClient).releaseSpotIfHeld(SPOT, hold.getValue());
	}

	private ParkingSession activeSession() {
		return activeSession(Bay.STANDARD);
	}

	private ParkingSession activeSession(Bay bay) {
		ParkingSession session = new ParkingSession(UUID.randomUUID(), "driver-1", SPOT, "A1", LOCATION, "Central Garage", bay, "CJ 07 ABC", null, ENTRY);
		when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
		return session;
	}

}
