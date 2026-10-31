package com.smartparking.parking.spot.controller;

import com.smartparking.parking.spot.dto.AddSpotsRequest;
import com.smartparking.parking.spot.dto.BulkSpotUpdateRequest;
import com.smartparking.parking.spot.dto.SpotResponse;
import com.smartparking.parking.spot.dto.SpotStatusRequest;
import com.smartparking.parking.spot.mapper.SpotMapper;
import com.smartparking.parking.spot.service.SpotService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking-locations/{locationId}/spots")
@RequiredArgsConstructor
public class SpotController {

	private final SpotService spotService;
	private final SpotMapper mapper;

	@GetMapping
	public List<SpotResponse> list(@PathVariable UUID locationId) {
		return spotService.list(locationId).stream().map(mapper::toResponse).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public List<SpotResponse> add(@PathVariable UUID locationId, @Valid @RequestBody AddSpotsRequest request) {
		return spotService.addSpots(locationId, request.level(), request.zone(), request.count(), request.type(),
				request.evCharging(), request.accessible()).stream().map(mapper::toResponse).toList();
	}

	/** Admin edit of one spot: its status (close or reopen) and/or its properties. */
	@PatchMapping("/{spotId}")
	public SpotResponse update(@PathVariable UUID locationId, @PathVariable UUID spotId,
			@Valid @RequestBody SpotStatusRequest request) {
		return mapper.toResponse(spotService.update(locationId, spotId, request.status(), request.type(), request.evCharging(),
				request.accessible()));
	}

	/** Admin edit of several spots at once, e.g. "these four now have EV charging". */
	@PatchMapping
	public List<SpotResponse> updateSeveral(@PathVariable UUID locationId, @Valid @RequestBody BulkSpotUpdateRequest request) {
		return spotService.changeProperties(locationId, request.spotIds(), request.type(), request.evCharging(), request.accessible())
			.stream().map(mapper::toResponse).toList();
	}

}
