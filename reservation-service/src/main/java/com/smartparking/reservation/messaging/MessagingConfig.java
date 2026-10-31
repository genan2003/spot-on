package com.smartparking.reservation.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
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
 * RabbitMQ topology owned by reservation-service: the shared topic exchange, its queue for payment
 * results, and the reservation expiry timer.
 *
 * <p>Expiry timer: each new reservation puts a message on {@link #EXPIRY_DELAY_QUEUE}, which nobody
 * consumes, with a per-message TTL equal to the hold time. When the TTL runs out RabbitMQ dead-letters
 * the message through {@link #EXPIRY_EXCHANGE} into {@link #EXPIRY_QUEUE}, where this service expires
 * the reservation if it is still active. Every message has the same TTL, so they expire in order.
 */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

	/**
	 * Messages a listener still couldn't handle after its retries go here instead of being dropped, with the
	 * original routing key and the exception in their headers. Shared topic exchange; one queue per service.
	 */
	public static final String DEAD_LETTER_EXCHANGE = "parking.dead-letters";

	public static final String DEAD_LETTER_QUEUE = "reservation-service.dlq";

	/** Routing key this service's dead letters are published under. */
	static final String DEAD_LETTER_ROUTING_KEY = "reservation-service";

	public static final String PAYMENT_RESULTS_QUEUE = "reservation.payment-results";

	/** Spots an admin closed while one of our reservations held them. */
	public static final String SPOT_CLOSURES_QUEUE = "reservation.spot-closures";

	public static final String EXPIRY_DELAY_QUEUE = "reservation.expiry-delay";

	public static final String EXPIRY_EXCHANGE = "reservation.expiry";

	public static final String EXPIRY_QUEUE = "reservation.expiry";

	/**
	 * Second timer, for "expires soon" reminders. Its own delay queue, because every message in a delay queue
	 * must have the same TTL to stay in order; it dead-letters through the same direct exchange.
	 */
	public static final String REMINDER_DELAY_QUEUE = "reservation.reminder-delay";

	public static final String REMINDER_QUEUE = "reservation.reminders";

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
	Declarables paymentResultsQueue(TopicExchange parkingEventsExchange) {
		Queue queue = QueueBuilder.durable(PAYMENT_RESULTS_QUEUE).build();
		return new Declarables(queue,
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("payment.completed"),
				BindingBuilder.bind(queue).to(parkingEventsExchange).with("payment.failed"));
	}

	@Bean
	Declarables spotClosuresQueue(TopicExchange parkingEventsExchange) {
		Queue queue = QueueBuilder.durable(SPOT_CLOSURES_QUEUE).build();
		return new Declarables(queue, BindingBuilder.bind(queue).to(parkingEventsExchange).with("spot.closed"));
	}

	@Bean
	Declarables expiryTimer() {
		DirectExchange expiryExchange = new DirectExchange(EXPIRY_EXCHANGE);
		Queue delayQueue = QueueBuilder.durable(EXPIRY_DELAY_QUEUE)
			.deadLetterExchange(EXPIRY_EXCHANGE)
			.deadLetterRoutingKey(EXPIRY_QUEUE)
			.build();
		Queue expiryQueue = QueueBuilder.durable(EXPIRY_QUEUE).build();
		Binding binding = BindingBuilder.bind(expiryQueue).to(expiryExchange).with(EXPIRY_QUEUE);
		Queue reminderDelayQueue = QueueBuilder.durable(REMINDER_DELAY_QUEUE)
			.deadLetterExchange(EXPIRY_EXCHANGE)
			.deadLetterRoutingKey(REMINDER_QUEUE)
			.build();
		Queue reminderQueue = QueueBuilder.durable(REMINDER_QUEUE).build();
		Binding reminderBinding = BindingBuilder.bind(reminderQueue).to(expiryExchange).with(REMINDER_QUEUE);
		return new Declarables(expiryExchange, delayQueue, expiryQueue, binding, reminderDelayQueue, reminderQueue, reminderBinding);
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
