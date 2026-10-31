package com.smartparking.reservation.messaging;

import com.smartparking.reservation.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). An admin took a reserved spot out of service: the
 * reservation can't be used any more, so it is cancelled, and the {@code reservation.cancelled} that
 * follows tells the driver why.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotClosedListener {

	private final ReservationService reservationService;
	private final ProcessedEvents processedEvents;

	@Transactional
	@RabbitListener(queues = MessagingConfig.SPOT_CLOSURES_QUEUE)
	public void onSpotClosed(SpotClosedEvent event) {
		if (!processedEvents.firstTime(event.eventId())) {
			log.debug("Skipping duplicate spot.closed {}", event.eventId());
			return;
		}
		boolean cancelled = reservationService.cancelBecauseSpotClosed(event.reservationId(), event.spotId());
		log.info("spot.closed for spot {}: {}", event.spotId(), cancelled ? "reservation cancelled" : "no active reservation there");
	}

}
