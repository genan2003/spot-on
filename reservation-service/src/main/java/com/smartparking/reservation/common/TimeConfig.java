package com.smartparking.reservation.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Time is injected rather than read directly, so tests can fix it. Scheduling runs the expiry sweep. */
@Configuration
@EnableScheduling
public class TimeConfig {

	/** The car parks' local time: night rates and daily statistics follow it, not UTC. */
	public static final ZoneId CAR_PARK_ZONE = ZoneId.of("Europe/Bucharest");

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
