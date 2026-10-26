package com.smartparking.parking.spot.event;

import com.smartparking.parking.messaging.MessagingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <b>Observer pattern: Observer</b> of the in-process event, publisher of {@code spot.status-changed}
 * on RabbitMQ. Runs after commit, so a rolled-back change (e.g. a lost optimistic-lock race) is never announced.
 */
@Component
@RequiredArgsConstructor
public class SpotStatusPublisher {

	private final RabbitTemplate rabbitTemplate;

	@TransactionalEventListener
	public void publish(SpotStatusChanged event) {
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, MessagingConfig.SPOT_STATUS_CHANGED, event);
	}

}
