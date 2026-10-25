package com.smartparking.poc.notification;

import lombok.extern.slf4j.Slf4j;

/** <b>Factory pattern: Concrete product.</b> Simulated Push delivery: writes the message to the log. */
@Slf4j
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        log.info("[PUSH to {}] {}", notification.getRecipient().getName() + "'s phone", notification.getMessage());
    }
}
