package com.smartparking.poc.notification;

import lombok.extern.slf4j.Slf4j;

/** <b>Factory pattern: Concrete product.</b> Simulated Email delivery: writes the message to the log. */
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        log.info("[EMAIL to {}] {}", notification.getRecipient().getEmail(), notification.getMessage());
    }
}
