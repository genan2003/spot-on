package com.smartparking.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;

/** 5 requests per minute: 5 in a row, then one every 12 seconds; each driver has their own bucket. */
class TokenBucketRateLimiterTests {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T12:00:00Z"));
	private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, Duration.ofMinutes(1), clock);

	@Test
	void allowsTheBurstThenRefusesWithRetryAfter() {
		for (int i = 0; i < 5; i++) {
			assertThat(ask("driver-1").isAllowed()).isTrue();
		}
		Response refused = ask("driver-1");

		assertThat(refused.isAllowed()).isFalse();
		assertThat(refused.getHeaders()).containsEntry("Retry-After", "12").containsEntry("X-RateLimit-Remaining", "0");
	}

	@Test
	void oneTokenComesBackPerTwelveSeconds() {
		for (int i = 0; i < 5; i++) {
			ask("driver-1");
		}
		clock.advance(Duration.ofSeconds(7));
		assertThat(ask("driver-1").getHeaders()).containsEntry("Retry-After", "5");

		clock.advance(Duration.ofSeconds(5));
		assertThat(ask("driver-1").isAllowed()).isTrue();
		assertThat(ask("driver-1").isAllowed()).isFalse();
	}

	@Test
	void aBucketNeverHoldsMoreThanItsCapacity() {
		ask("driver-1");
		clock.advance(Duration.ofHours(1));
		for (int i = 0; i < 5; i++) {
			assertThat(ask("driver-1").isAllowed()).isTrue();
		}
		assertThat(ask("driver-1").isAllowed()).isFalse();
	}

	@Test
	void driversDoNotShareABucket() {
		for (int i = 0; i < 6; i++) {
			ask("driver-1");
		}
		assertThat(ask("driver-2").isAllowed()).isTrue();
		assertThat(ask("driver-2").getHeaders()).containsEntry("X-RateLimit-Remaining", "3");
	}

	private Response ask(String driver) {
		return limiter.isAllowed("reserve-spot", driver).block();
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
