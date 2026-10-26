package com.smartparking.notification.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An event this service has already handled; its id is the primary key. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

	@Id
	private UUID eventId;

	private Instant processedAt;

	public ProcessedEvent(UUID eventId, Instant processedAt) {
		this.eventId = eventId;
		this.processedAt = processedAt;
	}

}
