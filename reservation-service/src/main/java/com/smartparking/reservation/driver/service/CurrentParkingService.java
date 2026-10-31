package com.smartparking.reservation.driver.service;

import com.smartparking.reservation.driver.dto.CurrentParkingResponse;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.mapper.ReservationMapper;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.domain.SessionStatus;
import com.smartparking.reservation.session.dto.SessionResponse;
import com.smartparking.reservation.session.mapper.SessionMapper;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import com.smartparking.reservation.session.service.ParkingSessionFacade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The driver app's home screen: the open reservation, the running session with its cost so far, and the
 * last thing that ended. Which ended thing is shown follows three rules:
 * <ol>
 *   <li>a receipt whose payment hasn't gone through (failed or still processing) always shows, because
 *       the driver may need to retry it,</li>
 *   <li>otherwise the newest of the last receipt and the last cancelled or expired reservation, so an
 *       old receipt never stands in for a reservation the driver just cancelled,</li>
 *   <li>and only for {@link #RECENT} after it ended; after that the screen invites a new search.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CurrentParkingService {

	static final Duration RECENT = Duration.ofHours(2);

	private static final List<ReservationStatus> ENDED_WITHOUT_PARKING = List.of(ReservationStatus.CANCELLED, ReservationStatus.EXPIRED);

	private final ReservationRepository reservationRepository;
	private final ParkingSessionRepository sessionRepository;
	private final ParkingSessionFacade sessionFacade;
	private final ReservationMapper reservationMapper;
	private final SessionMapper sessionMapper;
	private final Clock clock;

	public CurrentParkingResponse current(String driverId) {
		var reservation = reservationRepository.findFirstByDriverIdAndStatus(driverId, ReservationStatus.ACTIVE)
			.map(reservationMapper::toResponse)
			.orElse(null);
		SessionResponse session = sessionRepository.findFirstByDriverIdAndStatus(driverId, SessionStatus.ACTIVE)
			.map(this::withRunningCost)
			.orElse(null);

		ParkingSession lastSession = sessionRepository
			.findFirstByDriverIdAndStatusOrderByEndedAtDesc(driverId, SessionStatus.COMPLETED)
			.orElse(null);
		Reservation lastReservation = reservationRepository
			.findFirstByDriverIdAndStatusInAndEndedAtNotNullOrderByEndedAtDesc(driverId, ENDED_WITHOUT_PARKING)
			.orElse(null);
		Instant now = clock.instant();

		if (lastSession != null && lastSession.getPaymentStatus() != PaymentStatus.COMPLETED) {
			return new CurrentParkingResponse(reservation, session, sessionMapper.toResponse(lastSession), null);
		}
		boolean receiptIsNewer = lastSession != null
				&& (lastReservation == null || lastSession.getEndedAt().isAfter(lastReservation.getEndedAt()));
		if (receiptIsNewer && isRecent(lastSession.getEndedAt(), now)) {
			return new CurrentParkingResponse(reservation, session, sessionMapper.toResponse(lastSession), null);
		}
		if (!receiptIsNewer && lastReservation != null && isRecent(lastReservation.getEndedAt(), now)) {
			return new CurrentParkingResponse(reservation, session, null, reservationMapper.toResponse(lastReservation));
		}
		return new CurrentParkingResponse(reservation, session, null, null);
	}

	private static boolean isRecent(Instant endedAt, Instant now) {
		return endedAt.isAfter(now.minus(RECENT));
	}

	private SessionResponse withRunningCost(ParkingSession session) {
		return sessionMapper.toResponse(session).withCost(sessionFacade.calculateCost(session, clock.instant()));
	}

}
