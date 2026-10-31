package com.smartparking.notification.notification.live;

import com.smartparking.notification.notification.domain.Notification;
import com.smartparking.notification.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Observer pattern: Event</b> {@code notification.sent}. A notification was saved for a driver. Published by
 * notification-service and consumed by every notification-service instance, each forwarding it to the streams of
 * the drivers connected to it, so a driver gets it whichever instance their stream is on.
 */
public record NotificationSentEvent(UUID eventId, Instant occurredAt, String driverId, NotificationResponse notification) {

	public static NotificationSentEvent of(Notification n, NotificationResponse response) {
		return new NotificationSentEvent(UUID.randomUUID(), n.getSentAt(), n.getDriverId(), response);
	}

}
