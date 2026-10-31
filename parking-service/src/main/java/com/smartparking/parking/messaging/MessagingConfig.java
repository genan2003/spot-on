package com.smartparking.parking.messaging;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by parking-service. Every service declares the shared topic exchange
 * (same settings, so declaring it twice is harmless) plus its own queues.
 */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

	/**
	 * Messages a listener still couldn't handle after its retries go here instead of being dropped, with the
	 * original routing key and the exception in their headers. Shared topic exchange; one queue per service.
	 */
	public static final String DEAD_LETTER_EXCHANGE = "parking.dead-letters";

	public static final String DEAD_LETTER_QUEUE = "parking-service.dlq";

	/** Routing key this service's dead letters are published under. */
	static final String DEAD_LETTER_ROUTING_KEY = "parking-service";

	/** Durable: release events wait here while parking-service is down. */
	public static final String SPOT_HOLDS_QUEUE = "parking.spot-holds";

	public static final String SPOT_STATUS_CHANGED = "spot.status-changed";

	public static final String SPOT_CLOSED = "spot.closed";

	/** JSON; each consumer reads the payload into its own record type, so no event classes are shared. */
	@Bean
	MessageConverter messageConverter() {
		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
		converter.setAlwaysConvertToInferredType(true);
		return converter;
	}

	@Bean
	TopicExchange parkingEventsExchange() {
		return new TopicExchange(EXCHANGE);
	}

	/** Events that end a reservation or session, or move a reservation away from a spot: all free a spot. */
	@Bean
	Declarables spotHoldsQueue(TopicExchange parkingEventsExchange) {
		Queue queue = QueueBuilder.durable(SPOT_HOLDS_QUEUE).build();
		return new Declarables(queue,
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("reservation.cancelled"),
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("reservation.expired"),
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("reservation.changed"),
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("session.completed"));
	}

	/**
	 * One temporary queue per parking-service instance for spot.status-changed: every instance gets
	 * every change and forwards it to the browsers connected to it, so live updates still work
	 * with several instances behind the gateway.
	 */
	@Bean
	AnonymousQueue spotStatusQueue() {
		return new AnonymousQueue();
	}

	@Bean
	Binding spotStatusBinding(AnonymousQueue spotStatusQueue, TopicExchange parkingEventsExchange) {
		return BindingBuilder.bind(spotStatusQueue).to(parkingEventsExchange).with(SPOT_STATUS_CHANGED);
	}

	/**
	 * One temporary queue per instance for the admin's activity feed: every reservation, session and payment event,
	 * whatever else consumes it. Not durable: the feed is for watching live, nothing waits for it.
	 */
	@Bean
	AnonymousQueue activityQueue() {
		return new AnonymousQueue();
	}

	@Bean
	Declarables activityBindings(AnonymousQueue activityQueue, TopicExchange parkingEventsExchange) {
		return new Declarables(
				BindingBuilder.bind(activityQueue).to(parkingEventsExchange).with("reservation.*"),
				BindingBuilder.bind(activityQueue).to(parkingEventsExchange).with("session.*"),
				BindingBuilder.bind(activityQueue).to(parkingEventsExchange).with("payment.*"));
	}

	/**
	 * Dead-letter queue: durable, so failed messages wait here for someone to look at them (RabbitMQ UI, "Get
	 * messages") and publish them again once the cause is fixed. Declared on a separate exchange, so the
	 * existing queues keep their arguments and brokers that already have them don't refuse the declaration.
	 */
	@Bean
	Declarables deadLetters() {
		TopicExchange exchange = new TopicExchange(DEAD_LETTER_EXCHANGE);
		Queue queue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
		return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange).with(DEAD_LETTER_ROUTING_KEY));
	}

	/**
	 * Used by Spring Boot's listener retry (3 attempts, see {@code application.yml}): after the last attempt the
	 * message is republished to the dead-letter exchange with {@code x-exception-message},
	 * {@code x-exception-stacktrace}, {@code x-original-exchange} and {@code x-original-routingKey} headers.
	 */
	@Bean
	MessageRecoverer deadLetterRecoverer(RabbitTemplate rabbitTemplate) {
		return new RepublishMessageRecoverer(rabbitTemplate, DEAD_LETTER_EXCHANGE, DEAD_LETTER_ROUTING_KEY);
	}

}
