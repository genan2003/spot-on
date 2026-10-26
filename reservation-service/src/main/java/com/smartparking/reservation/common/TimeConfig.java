package com.smartparking.reservation.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Time is injected rather than read directly, so tests can fix it. Scheduling runs the expiry sweep. */
@Configuration
@EnableScheduling
public class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
