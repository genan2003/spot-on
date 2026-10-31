package com.smartparking.reservation.driver.dto;

import com.smartparking.reservation.reservation.dto.ReservationResponse;
import com.smartparking.reservation.session.dto.SessionResponse;

/**
 * What a driver currently holds: at most one reservation or running session. Besides that, the last thing
 * that ended, so "My parking" never shows something older than what the driver just did: either the last
 * receipt ({@code lastCompleted}) or a cancelled or expired reservation ({@code lastEndedReservation}),
 * never both.
 */
public record CurrentParkingResponse(ReservationResponse reservation, SessionResponse session, SessionResponse lastCompleted,
		ReservationResponse lastEndedReservation) {
}
