package com.smartparking.poc.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationChannelFactoryTest {

    private final NotificationChannelFactory factory = new NotificationChannelFactory();

    @Test
    void createsTheChannelForEachType() {
        assertThat(factory.create(ChannelType.EMAIL)).isInstanceOf(EmailNotificationChannel.class);
        assertThat(factory.create(ChannelType.SMS)).isInstanceOf(SmsNotificationChannel.class);
        assertThat(factory.create(ChannelType.PUSH)).isInstanceOf(PushNotificationChannel.class);
    }
}
