package com.smartparking.poc.notification;

import lombok.extern.slf4j.Slf4j;

/** <b>Factory pattern: Concrete product.</b> Simulated SMS delivery: writes the message to the log. */
@Slf4j
public class SmsNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        log.info("[SMS to {}] {}", notification.getRecipient().getPhone(), notification.getMessage());
    }
}
