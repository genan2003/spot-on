package com.smartparking.notification.notification.live;

import com.smartparking.notification.messaging.MessagingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <b>Observer pattern</b>: gets a saved notification to the driver's open streams on any instance. After the
 * commit it publishes {@code notification.sent}; every instance receives it on its own temporary queue and
 * forwards it to its local streams (same pattern as parking-service's live spot status).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationLiveRelay {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationStreams streams;

	@TransactionalEventListener(fallbackExecution = true)
	public void publish(NotificationSentEvent event) {
		try {
			rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, MessagingConfig.NOTIFICATION_SENT, event);
		}
		catch (AmqpException ex) {
			// The notification is saved; the driver sees it on the next refresh of the list.
			log.warn("Couldn't push notification {} live: {}", event.notification().id(), ex.getMessage());
		}
	}

	@RabbitListener(queues = "#{notificationLiveQueue.name}")
	public void forward(NotificationSentEvent event) {
		streams.send(event.driverId(), event.notification());
	}

}
