package com.smartparking.parking.spot.service;

import com.smartparking.parking.location.domain.ParkingLocation;
import com.smartparking.parking.location.service.ParkingLocationService;
import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import com.smartparking.parking.spot.event.SpotClosed;
import com.smartparking.parking.spot.event.SpotStatusChanged;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
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
		// Continue after the highest number in the zone, not the count: after deleting A2 of A1–A3,
		// the next spot is A4, not a second A3.
		int highest = spotRepository.findByLocationIdAndLevelAndZone(locationId, level, zone).stream()
			.mapToInt(ParkingSpot::getNumber)
			.max()
			.orElse(0);
		List<ParkingSpot> added = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			added.add(new ParkingSpot(location, zone + (highest + i), level, zone, type, evCharging, accessible));
		}
		return spotRepository.saveAll(added);
	}

	/** State pattern: the spot's current state decides whether it may close or reopen. */
	/**
	 * Admin edit of several spots' properties (type, EV charging, accessible); {@code null} keeps a property.
	 * Every spot must belong to the location. Live clients get each change on {@code spot.status-changed}.
	 */
	@Transactional
	public List<ParkingSpot> changeProperties(UUID locationId, Collection<UUID> spotIds, SpotType type, Boolean evCharging,
			Boolean accessible) {
		List<ParkingSpot> spots = spotRepository.findAllById(spotIds);
		if (spots.size() != spotIds.size() || spots.stream().anyMatch(s -> !s.getLocation().getId().equals(locationId))) {
			throw new EntityNotFoundException("Some of these spots don't exist at this location.");
		}
		spots.forEach(spot -> {
			spot.changeProperties(type, evCharging, accessible);
			eventPublisher.publishEvent(SpotStatusChanged.of(spot));
		});
		return spots.stream().sorted(ParkingSpot.ORDER).toList();
	}

	/**
	 * Removes a spot for good. Only a free or closed spot: a reserved or occupied one belongs to a driver
	 * right now (an admin can close a reserved spot first, which cancels the reservation).
	 * {@code @Version} turns a delete racing a reservation into a 409.
	 */
	@Transactional
	public void delete(UUID locationId, UUID spotId) {
		ParkingSpot spot = spotRepository.findByIdAndLocationId(spotId, locationId)
			.orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found at this location."));
		if (spot.getStatus() == SpotStatus.RESERVED || spot.getStatus() == SpotStatus.OCCUPIED) {
			throw new IllegalStateException("Spot " + spot.getCode() + " is " + spot.getStatus().name().toLowerCase()
					+ ". Wait until it is free, or close it first if it is only reserved.");
		}
		spotRepository.delete(spot);
	}

	/** One admin edit of one spot: properties first, then the status, in one transaction. */
	@Transactional
	public ParkingSpot update(UUID locationId, UUID spotId, SpotStatus status, SpotType type, Boolean evCharging, Boolean accessible) {
		ParkingSpot spot = spotRepository.findByIdAndLocationId(spotId, locationId)
			.orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found at this location."));
		if (type != null || evCharging != null || accessible != null) {
			changeProperties(locationId, List.of(spotId), type, evCharging, accessible);
		}
		return status == null ? spot : setStatus(locationId, spotId, status);
	}

	@Transactional
	public ParkingSpot setStatus(UUID locationId, UUID spotId, SpotStatus status) {
		ParkingSpot spot = spotRepository.findByIdAndLocationId(spotId, locationId)
			.orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found at this location."));
		// Remember the reservation before closing: the State pattern drops the hold with the transition.
		boolean wasReserved = spot.getStatus() == SpotStatus.RESERVED;
		UUID reservationId = spot.getHoldId();
		String driverId = spot.getHeldBy();
		switch (status) {
			case OUT_OF_SERVICE -> spot.markOutOfService();
			case AVAILABLE -> spot.returnToService();
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Admins can only set a spot to AVAILABLE or OUT_OF_SERVICE; drivers reserve and occupy spots.");
		}
		eventPublisher.publishEvent(SpotStatusChanged.of(spot));
		if (wasReserved && status == SpotStatus.OUT_OF_SERVICE) {
			eventPublisher.publishEvent(SpotClosed.of(spot, reservationId, driverId));
		}
		return spot;
	}

}
