package com.smartparking.parking.messaging;

import com.smartparking.parking.spot.service.SpotHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). Frees the spot when a reservation is cancelled
 * or expires, or when the car leaves. reservation-service doesn't wait for this: the spot turns free a
 * moment later, and if parking-service is down the events wait in the durable queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotHoldEventListener {

	private final SpotHoldService holdService;
	private final ProcessedEvents processedEvents;

	@Transactional
	@RabbitListener(queues = MessagingConfig.SPOT_HOLDS_QUEUE)
	public void onHoldEnded(HoldEndedEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		if (!processedEvents.firstTime(event.eventId())) {
			log.debug("Skipping duplicate {} {}", routingKey, event.eventId());
			return;
		}
		// A session holds the spot while the car is there; a reservation holds it before that.
		var holdId = routingKey.startsWith("session.") ? event.sessionId() : event.reservationId();
		boolean freed = holdService.releaseIfHeld(event.spotId(), holdId);
		log.info("{} for spot {}: {}", routingKey, event.spotId(), freed ? "spot freed" : "spot already moved on");
	}

}
