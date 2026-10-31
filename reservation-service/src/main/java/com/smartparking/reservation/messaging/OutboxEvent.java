package com.smartparking.reservation.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One event waiting to be sent to RabbitMQ, or already sent ({@link #sentAt}). See {@link OutboxRelay}. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	/** The event's {@code eventId}, so the row and the message can be matched. */
	@Id
	private UUID id;

	private String routingKey;

	/** The event as JSON, exactly as it will be sent. */
	private String payload;

	private Instant createdAt;

	private Instant sentAt;

	public OutboxEvent(UUID id, String routingKey, String payload, Instant createdAt) {
		this.id = id;
		this.routingKey = routingKey;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public void markSent(Instant at) {
		sentAt = at;
	}

}
