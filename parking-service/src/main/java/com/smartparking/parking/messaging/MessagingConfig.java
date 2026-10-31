package com.smartparking.parking.messaging;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
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

}
