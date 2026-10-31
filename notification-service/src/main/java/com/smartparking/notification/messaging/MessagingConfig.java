package com.smartparking.notification.messaging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
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

/** RabbitMQ topology owned by notification-service: the shared topic exchange and one queue for every driver-facing event. */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

	/**
	 * Messages a listener still couldn't handle after its retries go here instead of being dropped, with the
	 * original routing key and the exception in their headers. Shared topic exchange; one queue per service.
	 */
	public static final String DEAD_LETTER_EXCHANGE = "parking.dead-letters";

	public static final String DEAD_LETTER_QUEUE = "notification-service.dlq";

	/** Routing key this service's dead letters are published under. */
	static final String DEAD_LETTER_ROUTING_KEY = "notification-service";

	/** Durable: while notification-service is down, events wait here and are delivered once it restarts. */
	public static final String EVENTS_QUEUE = "notification.events";

	static final List<String> ROUTING_KEYS = List.of("reservation.created", "reservation.changed", "reservation.expiring", "reservation.cancelled", "reservation.expired",
			"session.completed", "payment.completed", "payment.failed");

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

	@Bean
	Declarables eventsQueue(TopicExchange parkingEventsExchange) {
		Queue queue = QueueBuilder.durable(EVENTS_QUEUE).build();
		List<Declarable> declarables = new ArrayList<>(List.of(queue));
		for (String routingKey : ROUTING_KEYS) {
			Binding binding = BindingBuilder.bind(queue).to(parkingEventsExchange).with(routingKey);
			declarables.add(binding);
		}
		return new Declarables(declarables);
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
