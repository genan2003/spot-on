package com.smartparking.reservation.reservation.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fallback sweep for overdue reservations. The TTL queue ({@code ReservationExpiryTimer}) normally
 * expires them on time; this catches any whose timer message was lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

	private final ReservationService reservationService;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${smartparking.reservation.expiry-check}")
	public void expireReservations() {
		int expired = reservationService.expireOverdue(clock.instant());
		if (expired > 0) {
			log.info("Expired {} reservation(s)", expired);
		}
	}

}
