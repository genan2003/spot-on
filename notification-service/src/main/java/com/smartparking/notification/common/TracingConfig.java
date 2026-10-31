package com.smartparking.notification.common;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps Zipkin readable: Docker's health checks (every few seconds) and timer runs ({@code @Scheduled}) are not
 * traced. Work a timer starts, such as sending an event, still shows up as its own trace.
 */
@Configuration
public class TracingConfig {

	@Bean
	ObservationPredicate skipHealthChecksAndTimers() {
		return (name, context) -> {
			if (name.equals("tasks.scheduled.execution")) {
				return false;
			}
			if (context instanceof ServerRequestObservationContext request) {
				return !request.getCarrier().getRequestURI().startsWith("/actuator");
			}
			return true;
		};
	}

}
