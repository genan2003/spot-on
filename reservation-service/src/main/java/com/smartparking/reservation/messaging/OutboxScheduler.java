package com.smartparking.reservation.messaging;

import com.smartparking.reservation.event.IntegrationEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When the {@link OutboxRelay} runs: right after each commit, so events still leave at once, and every half
 * second, to pick up whatever a crash or a RabbitMQ outage left in the outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

	private final OutboxRelay relay;

	@TransactionalEventListener
	public void afterCommit(IntegrationEvent event) {
		try {
			relay.sendPending();
		}
		catch (RuntimeException ex) {
			// The change is committed and the event saved; the poller sends it once RabbitMQ is reachable.
			log.warn("Couldn't send {} {} yet: {}", event.routingKey(), event.eventId(), ex.getMessage());
		}
	}

	@Scheduled(fixedDelayString = "${smartparking.outbox.poll-interval:500ms}")
	public void poll() {
		try {
			relay.sendPending();
		}
		catch (RuntimeException ex) {
			log.warn("Outbox relay failed, retrying on the next poll: {}", ex.getMessage());
		}
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
	public void cleanUp() {
		int deleted = relay.deleteSentBefore(Duration.ofDays(1));
		if (deleted > 0) {
			log.info("Deleted {} sent outbox events older than a day", deleted);
		}
	}

}
