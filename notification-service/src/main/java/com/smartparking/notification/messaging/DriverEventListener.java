package com.smartparking.notification.messaging;

import com.smartparking.notification.notification.domain.NotificationKind;
import com.smartparking.notification.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). Notifies the driver about every reservation,
 * session and payment event. The publishers don't know this service exists; if it is down,
 * reservations still work and the notifications arrive once it is back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverEventListener {

	private final NotificationService notificationService;
	private final ProcessedEvents processedEvents;

	@Transactional
	@RabbitListener(queues = MessagingConfig.EVENTS_QUEUE)
	public void onDriverEvent(DriverEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		if (!processedEvents.firstTime(event.eventId())) {
			log.debug("Skipping duplicate {} {}", routingKey, event.eventId());
			return;
		}
		NotificationKind kind = NotificationMessages.kind(routingKey);
		notificationService.send(event.driverId(), kind, NotificationMessages.message(kind, event));
	}

}
