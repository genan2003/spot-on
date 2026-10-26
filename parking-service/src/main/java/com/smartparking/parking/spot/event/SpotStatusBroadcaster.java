package com.smartparking.parking.spot.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * <b>Observer pattern: Observer.</b> Forwards every {@code spot.status-changed} event from this
 * instance's own queue to the browsers connected to it over STOMP, so the spot plan updates
 * without a reload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotStatusBroadcaster {

	static final String TOPIC = "/topic/spot-status";

	private final SimpMessagingTemplate messagingTemplate;

	@RabbitListener(queues = "#{spotStatusQueue.name}")
	public void broadcast(SpotStatusChanged event) {
		log.debug("Spot {} is now {}", event.spotId(), event.status());
		messagingTemplate.convertAndSend(TOPIC, event);
	}

}
