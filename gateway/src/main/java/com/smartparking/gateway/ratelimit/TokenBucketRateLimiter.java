package com.smartparking.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import reactor.core.publisher.Mono;

/**
 * In-memory token bucket for the gateway's {@code RequestRateLimiter} filter: each key (a driver) has a bucket of
 * {@code capacity} tokens that refills evenly over {@code period}. A request takes one token; with none left it is
 * refused (429) and {@code Retry-After} says when the next token arrives. With 5 per minute a driver can make
 * 5 attempts in a row, then one every 12 seconds.
 * <p>Kept in memory because there is one gateway instance; several instances would share the buckets in Redis
 * (Spring Cloud Gateway's {@code RedisRateLimiter}). Unused buckets are full again after {@code period}, so they
 * are dropped when the map grows.
 */
public class TokenBucketRateLimiter implements RateLimiter<Object> {

	private static final int MAX_BUCKETS = 10_000;

	private final int capacity;
	private final Duration timePerToken;
	private final Clock clock;
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	public TokenBucketRateLimiter(int capacity, Duration period, Clock clock) {
		if (capacity < 1 || period.isZero() || period.isNegative()) {
			throw new IllegalArgumentException("Rate limit needs at least 1 request per positive period.");
		}
		this.capacity = capacity;
		this.timePerToken = period.dividedBy(capacity);
		this.clock = clock;
	}

	@Override
	public Mono<Response> isAllowed(String routeId, String key) {
		return Mono.fromSupplier(() -> take(routeId + ":" + key));
	}

	private Response take(String key) {
		Instant now = clock.instant();
		if (buckets.size() > MAX_BUCKETS) {
			buckets.values().removeIf(bucket -> bucket.refilled(now, capacity, timePerToken).tokens() >= capacity);
		}
		// compute() runs atomically per key, so two requests from one driver can't take the same token.
		Bucket bucket = buckets.compute(key, (k, old) -> (old == null ? Bucket.full(capacity, now)
				: old.refilled(now, capacity, timePerToken)).take());

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("X-RateLimit-Limit", Integer.toString(capacity));
		headers.put("X-RateLimit-Remaining", Integer.toString((int) Math.floor(bucket.tokens())));
		if (!bucket.lastTakeAllowed()) {
			headers.put("Retry-After", Long.toString(secondsUntilNextToken(bucket)));
		}
		return new Response(bucket.lastTakeAllowed(), headers);
	}

	private long secondsUntilNextToken(Bucket bucket) {
		double missing = 1 - bucket.tokens();
		long nanos = (long) Math.ceil(missing * timePerToken.toNanos());
		return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
	}

	/** Not configured per route: one limit, set in application.yml. */
	@Override
	public Map<String, Object> getConfig() {
		return Map.of();
	}

	@Override
	public Class<Object> getConfigClass() {
		return Object.class;
	}

	@Override
	public Object newConfig() {
		return new Object();
	}

	/** Tokens left (fractions build up between requests) at time {@code at}; immutable, replaced on each request. */
	record Bucket(double tokens, Instant at, boolean lastTakeAllowed) {

		static Bucket full(int capacity, Instant now) {
			return new Bucket(capacity, now, true);
		}

		Bucket refilled(Instant now, int capacity, Duration timePerToken) {
			double earned = (double) Duration.between(at, now).toNanos() / timePerToken.toNanos();
			return new Bucket(Math.min(capacity, tokens + Math.max(0, earned)), now, lastTakeAllowed);
		}

		Bucket take() {
			return tokens >= 1 ? new Bucket(tokens - 1, at, true) : new Bucket(tokens, at, false);
		}

	}

}
