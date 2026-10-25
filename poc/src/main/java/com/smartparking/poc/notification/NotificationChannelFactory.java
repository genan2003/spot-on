package com.smartparking.poc.notification;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * <b>Factory pattern: Factory</b> (simple factory). Returns the channel for the driver's
 * preferred channel type, so the listener depends only on {@link NotificationChannel}.
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
