package com.smartparking.gateway;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * The gateway starts each request's trace and passes it on to the service it routes to ({@code traceparent}
 * header). Docker's health checks are not traced, so Zipkin only shows real requests.
 */
@Configuration
public class TracingConfig {

	@Bean
	ObservationPredicate skipHealthChecks() {
		return (name, context) -> !(context instanceof ServerRequestObservationContext request)
				|| !request.getCarrier().getPath().value().startsWith("/actuator");
	}

}
