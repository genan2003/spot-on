package com.smartparking.notification.notification.channel;

import com.smartparking.notification.notification.domain.Notification;

/** <b>Factory pattern: Product.</b> Delivers a notification. All channels are simulated with logs. */
public interface NotificationChannel {

	void send(Notification notification);

}
