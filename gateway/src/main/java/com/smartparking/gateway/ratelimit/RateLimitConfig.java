package com.smartparking.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Rate limit for reserving (route {@code reserve-spot} in application.yml): stops a driver, or a script, from
 * firing reservation attempts in a loop before they reach reservation-service and parking-service.
 */
@Configuration
public class RateLimitConfig {

	static final String DRIVER_HEADER = "X-Driver-Id";

	@Bean
	TokenBucketRateLimiter reservationRateLimiter(
			@Value("${smartparking.rate-limit.reservations.requests}") int requests,
			@Value("${smartparking.rate-limit.reservations.period}") Duration period) {
		return new TokenBucketRateLimiter(requests, period, Clock.systemUTC());
	}

	/**
	 * One bucket per driver. There is no login, so the driver id is whatever the client sends; a request without
	 * it is counted per client IP instead (with real accounts this would be the authenticated user).
	 */
	@Bean
	KeyResolver driverKeyResolver() {
		return exchange -> {
			String driverId = exchange.getRequest().getHeaders().getFirst(DRIVER_HEADER);
			if (StringUtils.hasText(driverId)) {
				return Mono.just("driver:" + driverId);
			}
			return Mono.just("ip:" + Optional.ofNullable(exchange.getRequest().getRemoteAddress())
				.map(InetSocketAddress::getAddress)
				.map(Object::toString)
				.orElse("unknown"));
		};
	}

}
