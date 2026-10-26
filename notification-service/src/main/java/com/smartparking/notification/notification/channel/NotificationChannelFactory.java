package com.smartparking.notification.notification.channel;

import com.smartparking.notification.notification.domain.ChannelType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * <b>Factory pattern: Factory</b> (simple factory). Returns the channel for a channel type, so
 * {@code NotificationService} depends only on {@link NotificationChannel}.
 */
@Component
public class NotificationChannelFactory {

	private final Map<ChannelType, NotificationChannel> channels = Map.of(
			ChannelType.EMAIL, new EmailNotificationChannel(),
			ChannelType.SMS, new SmsNotificationChannel(),
			ChannelType.PUSH, new PushNotificationChannel());

	public NotificationChannel create(ChannelType type) {
		return channels.get(type);
	}

}
