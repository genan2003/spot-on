package com.smartparking.parking.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Time is injected rather than read directly, so tests can fix it. */
@Configuration
public class TimeConfig {

	/** The car parks' local time: opening hours follow it, not UTC. Same zone as reservation-service's pricing. */
	public static final ZoneId CAR_PARK_ZONE = ZoneId.of("Europe/Bucharest");

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
