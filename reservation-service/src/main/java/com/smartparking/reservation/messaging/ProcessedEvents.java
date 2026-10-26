package com.smartparking.reservation.messaging;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes consumers idempotent. RabbitMQ delivers at least once, so a listener can see the same event
 * twice (redelivery after a crash, a retry). The event id is recorded in the listener's own
 * transaction: the effect and the record commit together, or neither does.
 */
@Component
@RequiredArgsConstructor
public class ProcessedEvents {

	private final ProcessedEventRepository repository;

	/** {@code true} the first time an event id is seen; {@code false} for a duplicate, which the caller skips. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean firstTime(UUID eventId) {
		if (repository.existsById(eventId)) {
			return false;
		}
		repository.save(new ProcessedEvent(eventId, Instant.now()));
		return true;
	}

}
