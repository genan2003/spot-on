package com.smartparking.reservation.reservation.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fallback sweep for overdue reservations and "expires soon" reminders. The TTL queues
 * ({@code ReservationExpiryTimer}, {@code ReservationReminderTimer}) normally act on time; this catches any whose
 * timer message was lost or is stuck behind older messages with a longer TTL (after the hold time changed).
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
		int reminded = reservationService.remindDue(clock.instant());
		if (reminded > 0) {
			log.info("Reminded {} driver(s) that their reservation expires soon", reminded);
		}
	}

}
