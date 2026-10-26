package com.smartparking.parking.spot.service;

import com.smartparking.parking.location.domain.ParkingLocation;
import com.smartparking.parking.location.service.ParkingLocationService;
import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import com.smartparking.parking.spot.event.SpotStatusChanged;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin operations on the spots of a location: list, add, close and reopen. */
@Service
@RequiredArgsConstructor
public class SpotService {

	private final ParkingSpotRepository spotRepository;
	private final ParkingLocationService locationService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional(readOnly = true)
	public List<ParkingSpot> list(UUID locationId) {
		locationService.find(locationId);
		return spotRepository.findByLocationIdOrdered(locationId);
	}

	/** Adds {@code count} spots to a zone, numbered after the ones already there (A1, A2, ...). */
	@Transactional
	public List<ParkingSpot> addSpots(UUID locationId, int level, String zone, int count, SpotType type,
			boolean evCharging, boolean accessible) {
		ParkingLocation location = locationService.find(locationId);
		long existing = spotRepository.countByLocationIdAndLevelAndZone(locationId, level, zone);
		List<ParkingSpot> added = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			added.add(new ParkingSpot(location, zone + (existing + i), level, zone, type, evCharging, accessible));
		}
		return spotRepository.saveAll(added);
	}

	/** State pattern: the spot's current state decides whether it may close or reopen. */
	@Transactional
	public ParkingSpot setStatus(UUID locationId, UUID spotId, SpotStatus status) {
		ParkingSpot spot = spotRepository.findByIdAndLocationId(spotId, locationId)
			.orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found at this location."));
		switch (status) {
			case OUT_OF_SERVICE -> spot.markOutOfService();
			case AVAILABLE -> spot.returnToService();
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Admins can only set a spot to AVAILABLE or OUT_OF_SERVICE; drivers reserve and occupy spots.");
		}
		eventPublisher.publishEvent(SpotStatusChanged.of(spot));
		return spot;
	}

}
