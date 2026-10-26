package com.smartparking.notification.notification.controller;

import com.smartparking.notification.notification.dto.NotificationResponse;
import com.smartparking.notification.notification.mapper.NotificationMapper;
import com.smartparking.notification.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationMapper mapper;

	@GetMapping
	public List<NotificationResponse> list(@RequestParam String driverId) {
		return notificationService.list(driverId).stream().map(mapper::toResponse).toList();
	}

}
