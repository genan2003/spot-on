package com.smartparking.reservation.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient for parking-service, with timeouts: a slow parking-service gives the driver a clear
 * 503 after a few seconds instead of a request that hangs. Around it, Resilience4j:
 * <ul>
 *   <li><b>Retry</b>: a call that got no answer or a 5xx is tried again after a short wait. Safe because
 *       every call names its hold id, and parking-service treats a repeated reserve/occupy of the same
 *       hold as already done. A 409 or 404 is an answer, so it is never retried.</li>
 *   <li><b>Circuit breaker</b>: when at least half of the recent calls failed, it opens and further calls
 *       fail at once (no waiting for timeouts while parking-service is down). After {@code open-for} it
 *       lets a few calls through to test whether parking-service is back.</li>
 * </ul>
 * Only the core Resilience4j modules are used, set up here in code: no annotations or AOP to explain.
 */
@Slf4j
@Configuration
public class ParkingClientConfig {

	@Bean
	RestClient parkingRestClient(@Value("${smartparking.parking-service.url}") String baseUrl,
			@Value("${smartparking.parking-service.connect-timeout}") Duration connectTimeout,
			@Value("${smartparking.parking-service.read-timeout}") Duration readTimeout) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(connectTimeout).build());
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	@Bean
	Retry parkingRetry(@Value("${smartparking.parking-service.retry.max-attempts}") int maxAttempts,
			@Value("${smartparking.parking-service.retry.wait}") Duration wait) {
		Retry retry = Retry.of("parking-service", RetryConfig.custom()
			.maxAttempts(maxAttempts)
			.waitDuration(wait)
			// Not CallNotPermittedException: an open circuit answers at once, retrying it would only add waits.
			.retryExceptions(ParkingServiceUnavailableException.class)
			.build());
		retry.getEventPublisher()
			.onRetry(event -> log.info("Retrying parking-service call (attempt {}): {}", event.getNumberOfRetryAttempts() + 1,
					event.getLastThrowable().getMessage()));
		return retry;
	}

	@Bean
	CircuitBreaker parkingCircuitBreaker(
			@Value("${smartparking.parking-service.circuit-breaker.failure-rate-threshold}") float failureRateThreshold,
			@Value("${smartparking.parking-service.circuit-breaker.sliding-window-size}") int slidingWindowSize,
			@Value("${smartparking.parking-service.circuit-breaker.minimum-calls}") int minimumCalls,
			@Value("${smartparking.parking-service.circuit-breaker.open-for}") Duration openFor) {
		CircuitBreaker circuitBreaker = CircuitBreaker.of("parking-service", CircuitBreakerConfig.custom()
			.failureRateThreshold(failureRateThreshold)
			.slidingWindowSize(slidingWindowSize)
			.minimumNumberOfCalls(minimumCalls)
			.waitDurationInOpenState(openFor)
			.permittedNumberOfCallsInHalfOpenState(2)
			.automaticTransitionFromOpenToHalfOpenEnabled(true)
			// Only "no answer" counts as a failure; a 409 or 404 means parking-service is working.
			.recordExceptions(ParkingServiceUnavailableException.class)
			.build());
		circuitBreaker.getEventPublisher()
			.onStateTransition(event -> log.warn("parking-service circuit breaker: {}", event.getStateTransition()));
		return circuitBreaker;
	}

}
