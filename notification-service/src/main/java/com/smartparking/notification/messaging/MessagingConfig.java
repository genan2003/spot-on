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
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ topology owned by notification-service: the shared topic exchange and one queue for every driver-facing event. */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

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

}
