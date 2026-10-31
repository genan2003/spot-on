package com.smartparking.parking.activity.messaging;

import com.smartparking.parking.activity.service.ActivityFeed;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). Watches every reservation, session and payment event for the
 * admin's activity feed. It changes nothing, so it needs no {@code processed_event} row; the feed itself drops a
 * redelivered event.
 */
@Component
@RequiredArgsConstructor
public class ActivityListener {

	private final ActivityFeed feed;

	@RabbitListener(queues = "#{activityQueue.name}")
	public void onEvent(ActivityEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		feed.add(event.toItem(routingKey));
	}

}
