package com.smartparking.poc.notification;

/** <b>Factory pattern: Product.</b> Delivers a notification. All channels are simulated with logs. */
public interface NotificationChannel {

    void send(Notification notification);
}
