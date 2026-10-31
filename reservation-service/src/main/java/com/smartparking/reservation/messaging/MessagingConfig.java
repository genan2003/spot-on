package com.smartparking.reservation.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
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

	public static final String PAYMENT_RESULTS_QUEUE = "reservation.payment-results";

	/** Spots an admin closed while one of our reservations held them. */
	public static final String SPOT_CLOSURES_QUEUE = "reservation.spot-closures";

	public static final String EXPIRY_DELAY_QUEUE = "reservation.expiry-delay";

	public static final String EXPIRY_EXCHANGE = "reservation.expiry";

	public static final String EXPIRY_QUEUE = "reservation.expiry";

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
		return new Declarables(expiryExchange, delayQueue, expiryQueue, binding);
	}

}
