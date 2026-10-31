package com.smartparking.notification.preference.controller;

import com.smartparking.notification.preference.dto.NotificationPreferenceResponse;
import com.smartparking.notification.preference.dto.UpdateNotificationPreferenceRequest;
import com.smartparking.notification.preference.mapper.NotificationPreferenceMapper;
import com.smartparking.notification.preference.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A driver's notification channel (email, SMS or push, all simulated). */
@RestController
@RequestMapping("/api/v1/notification-preferences/{driverId}")
@RequiredArgsConstructor
public class NotificationPreferenceController {

	private final NotificationPreferenceService preferenceService;
	private final NotificationPreferenceMapper mapper;

	@GetMapping
	public NotificationPreferenceResponse get(@PathVariable String driverId) {
		return mapper.toResponse(preferenceService.get(driverId));
	}

	/** Chooses the channel for the driver's next notifications. */
	@PatchMapping
	public NotificationPreferenceResponse choose(@PathVariable String driverId,
			@Valid @RequestBody UpdateNotificationPreferenceRequest request) {
		return mapper.toResponse(preferenceService.choose(driverId, request.channel()));
	}

}
