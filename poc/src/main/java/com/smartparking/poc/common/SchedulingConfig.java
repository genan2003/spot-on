package com.smartparking.poc.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the reservation expiry check on a timer. The demo triggers it by hand instead. */
@Configuration
@Profile("!demo")
@EnableScheduling
public class SchedulingConfig {
}
