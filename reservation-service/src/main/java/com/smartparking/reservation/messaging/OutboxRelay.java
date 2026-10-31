package com.smartparking.reservation.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second half of the transactional outbox: sends saved events to the {@code parking.events} exchange and marks
 * them sent, in one transaction. If sending fails, the transaction rolls back and the rows stay unsent for the
 * next run, so delivery is at least once; consumers dedupe on {@code eventId}. {@link OutboxScheduler} decides
 * when it runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	private static final int BATCH = 50;

	private final OutboxEventRepository outbox;
	private final RabbitTemplate rabbitTemplate;
	private final Clock clock;

	/**
	 * Sends up to {@value #BATCH} unsent events, oldest first; returns how many. Always a new transaction:
	 * after-commit listeners may not reuse the caller's finished one.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int sendPending() {
		List<OutboxEvent> pending = outbox.lockUnsent(BATCH);
		for (OutboxEvent event : pending) {
			MessageProperties properties = new MessageProperties();
			properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			properties.setContentEncoding(StandardCharsets.UTF_8.name());
			properties.setMessageId(event.getId().toString());
			rabbitTemplate.send(MessagingConfig.EXCHANGE, event.getRoutingKey(),
					new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
			event.markSent(clock.instant());
			log.info("Published {} {}", event.getRoutingKey(), event.getId());
		}
		return pending.size();
	}

	/** Sent rows are only history; a day of them is kept for looking into problems. */
	@Transactional
	public int deleteSentBefore(Duration age) {
		return outbox.deleteSentBefore(clock.instant().minus(age));
	}

}
