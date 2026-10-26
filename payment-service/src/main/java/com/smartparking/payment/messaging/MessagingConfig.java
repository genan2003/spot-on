package com.smartparking.payment.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ topology owned by payment-service: the shared topic exchange and its queue of completed sessions. */
@Configuration
public class MessagingConfig {

	public static final String EXCHANGE = "parking.events";

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

}
