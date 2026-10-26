package com.smartparking.reservation.driver.service;

import com.smartparking.reservation.driver.dto.CurrentParkingResponse;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.mapper.ReservationMapper;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.SessionStatus;
import com.smartparking.reservation.session.dto.SessionResponse;
import com.smartparking.reservation.session.mapper.SessionMapper;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import com.smartparking.reservation.session.service.ParkingSessionFacade;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The driver app's home screen: the open reservation, the running session with its cost so far, and the last receipt. */
@Service
@RequiredArgsConstructor
public class CurrentParkingService {

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
		SessionResponse lastCompleted = sessionRepository
			.findFirstByDriverIdAndStatusOrderByEndedAtDesc(driverId, SessionStatus.COMPLETED)
			.map(sessionMapper::toResponse)
			.orElse(null);
		return new CurrentParkingResponse(reservation, session, lastCompleted);
	}

	private SessionResponse withRunningCost(ParkingSession session) {
		return sessionMapper.toResponse(session).withCost(sessionFacade.calculateCost(session, clock.instant()));
	}

}
