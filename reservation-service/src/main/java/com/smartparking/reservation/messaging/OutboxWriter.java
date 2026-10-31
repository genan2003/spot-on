package com.smartparking.reservation.messaging;

import com.smartparking.reservation.event.IntegrationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern</b>, first half of the bridge from in-process events to RabbitMQ (transactional outbox).
 * Services only call {@code ApplicationEventPublisher.publishEvent}; this listener runs in the caller's
 * transaction and saves the event to {@code outbox_event}, so the change and its event commit together.
 * {@link OutboxRelay} sends it afterwards.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

	private final OutboxEventRepository outbox;
	private final MessageConverter messageConverter;
	private final Clock clock;

	/** MANDATORY: an event published outside a transaction is a bug (it could be saved without its change), so it fails loudly. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void write(IntegrationEvent event) {
		// The same JSON RabbitMQ would have received directly.
		byte[] json = messageConverter.toMessage(event, new MessageProperties()).getBody();
		outbox.save(new OutboxEvent(event.eventId(), event.routingKey(), new String(json, StandardCharsets.UTF_8), clock.instant()));
	}

}
