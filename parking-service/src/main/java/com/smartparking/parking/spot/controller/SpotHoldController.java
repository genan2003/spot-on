package com.smartparking.parking.spot.controller;

import com.smartparking.parking.spot.dto.HeldSpotResponse;
import com.smartparking.parking.spot.dto.HoldSpotRequest;
import com.smartparking.parking.spot.dto.ReleaseSpotRequest;
import com.smartparking.parking.spot.mapper.SpotMapper;
import com.smartparking.parking.spot.service.SpotHoldService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for reservation-service (service to service, not routed by the gateway).
 * Each call is one spot state change; {@code 409} means the spot's state refused it.
 * Normal releases arrive as events; {@code /release} is only for undoing a failed step.
 */
@RestController
@RequestMapping("/api/v1/spots/{spotId}")
@RequiredArgsConstructor
public class SpotHoldController {

	private final SpotHoldService holdService;
	private final SpotMapper mapper;

	@PostMapping("/reserve")
	public HeldSpotResponse reserve(@PathVariable UUID spotId, @Valid @RequestBody HoldSpotRequest request) {
		return mapper.toHeldResponse(holdService.reserve(spotId, request.driverId(), request.holdId()));
	}

	@PostMapping("/occupy")
	public HeldSpotResponse occupy(@PathVariable UUID spotId, @Valid @RequestBody HoldSpotRequest request) {
		return mapper.toHeldResponse(holdService.occupy(spotId, request.driverId(), request.holdId()));
	}

	@PostMapping("/release")
	public HeldSpotResponse release(@PathVariable UUID spotId, @Valid @RequestBody ReleaseSpotRequest request) {
		return mapper.toHeldResponse(holdService.release(spotId, request.holdId()));
	}

}
