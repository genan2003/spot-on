package com.smartparking.notification.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}: the heartbeat that keeps idle notification streams open. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
