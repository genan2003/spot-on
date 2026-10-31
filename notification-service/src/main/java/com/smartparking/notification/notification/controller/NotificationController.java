package com.smartparking.notification.notification.controller;

import com.smartparking.notification.notification.dto.NotificationResponse;
import com.smartparking.notification.notification.live.NotificationStreams;
import com.smartparking.notification.notification.mapper.NotificationMapper;
import com.smartparking.notification.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationMapper mapper;
	private final NotificationStreams streams;

	/**
	 * Live notifications as Server-Sent Events: one {@code notification} event per new notification, its data
	 * the same JSON as in the list. The browser's {@code EventSource} reconnects by itself.
	 */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> stream(@RequestParam String driverId) {
		// Tells nginx not to buffer the stream, so each event reaches the browser at once.
		return ResponseEntity.ok().header("X-Accel-Buffering", "no").body(streams.open(driverId));
	}

	@GetMapping
	public List<NotificationResponse> list(@RequestParam String driverId) {
		return notificationService.list(driverId).stream().map(mapper::toResponse).toList();
	}

}
