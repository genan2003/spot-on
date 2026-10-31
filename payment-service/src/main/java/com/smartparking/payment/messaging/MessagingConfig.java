package com.smartparking.payment.messaging;

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

/** RabbitMQ topology owned by payment-service: the shared topic exchange and its queue of completed sessions. */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

	/**
	 * Messages a listener still couldn't handle after its retries go here instead of being dropped, with the
	 * original routing key and the exception in their headers. Shared topic exchange; one queue per service.
	 */
	public static final String DEAD_LETTER_EXCHANGE = "parking.dead-letters";

	public static final String DEAD_LETTER_QUEUE = "payment-service.dlq";

	/** Routing key this service's dead letters are published under. */
	static final String DEAD_LETTER_ROUTING_KEY = "payment-service";

	/** Durable: sessions that end while payment-service is down are charged when it comes back. */
	public static final String SESSIONS_QUEUE = "payment.session-completed";

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
	Declarables sessionsQueue(TopicExchange parkingEventsExchange) {
		Queue queue = QueueBuilder.durable(SESSIONS_QUEUE).build();
		return new Declarables(queue, BindingBuilder.bind(queue).to(parkingEventsExchange).with("session.completed"));
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
