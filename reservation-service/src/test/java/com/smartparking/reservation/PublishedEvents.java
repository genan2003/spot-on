package com.smartparking.reservation;

import com.jayway.jsonpath.JsonPath;
import com.smartparking.reservation.messaging.MessagingConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Test helper: a temporary queue that receives every event on parking.events, to check what was published. */
public class PublishedEvents {

	private final RabbitTemplate rabbitTemplate;
	/**
	 * Durable and not auto-delete: receiving with a timeout briefly subscribes, and unsubscribing would
	 * delete an auto-delete queue; RabbitMQ 4 no longer allows non-durable shared queues.
	 */
	private final Queue queue = new Queue("test.events." + UUID.randomUUID(), true, false, false);

	public PublishedEvents(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
		RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
		admin.declareQueue(queue);
		admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(MessagingConfig.EXCHANGE)).with("#"));
	}

	/** Waits for an event with this routing key whose JSON contains {@code text}; returns its JSON. */
	public String await(String routingKey, String text) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			Message message = rabbitTemplate.receive(queue.getName(), 500);
			if (message == null) {
				continue;
			}
			String json = new String(message.getBody(), StandardCharsets.UTF_8);
			if (routingKey.equals(message.getMessageProperties().getReceivedRoutingKey()) && json.contains(text)) {
				return json;
			}
		}
		throw new AssertionError("No " + routingKey + " event containing " + text);
	}

	public static <T> T read(String json, String path) {
		return JsonPath.read(json, path);
	}

}
