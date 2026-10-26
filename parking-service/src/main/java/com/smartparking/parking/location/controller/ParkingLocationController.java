package com.smartparking.parking.location.controller;

import com.smartparking.parking.location.dto.LocationRequest;
import com.smartparking.parking.location.dto.LocationResponse;
import com.smartparking.parking.location.dto.PricingRuleDto;
import com.smartparking.parking.location.mapper.LocationMapper;
import com.smartparking.parking.location.service.ParkingLocationService;
import com.smartparking.parking.spot.domain.SpotStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking-locations")
@RequiredArgsConstructor
public class ParkingLocationController {

	private final ParkingLocationService locationService;
	private final LocationMapper mapper;

	@GetMapping
	public List<LocationResponse> list() {
		return locationService.list().stream().map(mapper::toResponse).toList();
	}

	@GetMapping("/{id}")
	public LocationResponse get(@PathVariable UUID id) {
		return mapper.toResponse(locationService.get(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LocationResponse create(@Valid @RequestBody LocationRequest request) {
		return mapper.toResponse(locationService.create(request.name(), request.address(), request.latitude(), request.longitude()));
	}

	@PutMapping("/{id}")
	public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
		return mapper.toResponse(locationService.update(id, request.name(), request.address(), request.latitude(), request.longitude()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		locationService.delete(id);
	}

	@PutMapping("/{id}/pricing-rule")
	public LocationResponse updatePricingRule(@PathVariable UUID id, @Valid @RequestBody PricingRuleDto request) {
		return mapper.toResponse(locationService.updatePricingRule(id, mapper.toPricingRule(request)));
	}

	@GetMapping("/{id}/availability")
	public Map<SpotStatus, Long> availability(@PathVariable UUID id) {
		return locationService.availability(id);
	}

}
