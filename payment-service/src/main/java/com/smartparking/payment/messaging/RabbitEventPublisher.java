package com.smartparking.payment.messaging;

import com.smartparking.payment.event.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <b>Observer pattern</b>, the bridge from in-process events to RabbitMQ. Services only call
 * {@code ApplicationEventPublisher.publishEvent}; this listener sends the event to the
 * {@code parking.events} exchange once the change is committed (or straight away when the caller
 * had no transaction, e.g. after a repository save that committed by itself).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher {

	private final RabbitTemplate rabbitTemplate;

	@TransactionalEventListener(fallbackExecution = true)
	public void publish(IntegrationEvent event) {
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, event.routingKey(), event);
		log.info("Published {} {}", event.routingKey(), event.eventId());
	}

}
