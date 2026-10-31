package com.smartparking.notification.notification.live;

import com.smartparking.notification.notification.dto.NotificationResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The drivers' open notification streams (Server-Sent Events) on this instance, per driver: a driver can have
 * the app open in several tabs. {@link #send} pushes a notification to all of them.
 */
@Slf4j
@Component
public class NotificationStreams {

	/** The browser reconnects on its own when a stream ends, so streams don't have to live forever. */
	private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

	private final Map<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

	public SseEmitter open(String driverId) {
		SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
		streams.computeIfAbsent(driverId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
		Runnable close = () -> remove(driverId, emitter);
		emitter.onCompletion(close);
		emitter.onTimeout(close);
		emitter.onError(error -> close.run());
		// A first event, so the browser knows the stream is open (and proxies flush their headers).
		deliver(driverId, emitter, SseEmitter.event().comment("connected"));
		return emitter;
	}

	public void send(String driverId, NotificationResponse notification) {
		for (SseEmitter emitter : streams.getOrDefault(driverId, Set.of())) {
			deliver(driverId, emitter, SseEmitter.event().name("notification").id(notification.id().toString()).data(notification));
		}
	}

	/** A comment every 20 seconds keeps idle streams open through nginx and the gateway. */
	@Scheduled(fixedDelay = 20_000)
	public void heartbeat() {
		streams.forEach((driverId, emitters) -> emitters.forEach(emitter -> deliver(driverId, emitter, SseEmitter.event().comment("ping"))));
	}

	public int openStreams() {
		return streams.values().stream().mapToInt(Set::size).sum();
	}

	private void deliver(String driverId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try {
			emitter.send(event);
		}
		catch (IOException | IllegalStateException ex) {
			// The browser went away; forget the stream.
			log.debug("Dropping a closed notification stream of {}", driverId);
			remove(driverId, emitter);
		}
	}

	private void remove(String driverId, SseEmitter emitter) {
		streams.computeIfPresent(driverId, (id, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

}
