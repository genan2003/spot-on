package com.smartparking.reservation.driver.dto;

import com.smartparking.reservation.reservation.dto.ReservationResponse;
import com.smartparking.reservation.session.dto.SessionResponse;

/** What a driver currently holds: at most one reservation or running session, plus the last receipt. */
public record CurrentParkingResponse(ReservationResponse reservation, SessionResponse session, SessionResponse lastCompleted) {
}
