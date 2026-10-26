package com.smartparking.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> that leaves this service. Services publish it in-process; after
 * the commit {@code RabbitEventPublisher} sends it to the {@code parking.events} exchange under
 * {@link #routingKey()}. {@code eventId} lets consumers ignore a duplicate delivery.
 */
public interface IntegrationEvent {

	UUID eventId();

	Instant occurredAt();

	/** {@code <aggregate>.<past-tense-verb>}, e.g. {@code reservation.created}. Not part of the JSON. */
	String routingKey();

}
