package com.smartparking.parking.location.service;

import com.smartparking.parking.location.domain.LocationSummary;
import com.smartparking.parking.location.domain.ParkingLocation;
import com.smartparking.parking.location.domain.PricingRule;
import com.smartparking.parking.location.repository.ParkingLocationRepository;
import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import com.smartparking.parking.spot.repository.SpotCount;
import jakarta.persistence.EntityNotFoundException;
import java.util.EnumMap;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin operations on locations and their pricing, plus the availability counts drivers see. */
@Service
@RequiredArgsConstructor
public class ParkingLocationService {

	private final ParkingLocationRepository locationRepository;
	private final ParkingSpotRepository spotRepository;

	@Transactional(readOnly = true)
	public List<LocationSummary> list() {
		Map<UUID, List<SpotCount>> counts = spotRepository.countByLocationAndStatus().stream()
			.collect(Collectors.groupingBy(SpotCount::locationId));
		return locationRepository.findAllByOrderByNameAsc().stream()
			.map(location -> summarize(location, counts.getOrDefault(location.getId(), List.of())))
			.toList();
	}

	@Transactional(readOnly = true)
	public LocationSummary get(UUID id) {
		ParkingLocation location = find(id);
		return summarize(location, spotRepository.countByStatus(id));
	}

	@Transactional
	public LocationSummary create(String name, String address, double latitude, double longitude, LocalTime opensAt,
			LocalTime closesAt) {
		ParkingLocation location = new ParkingLocation(name, address, latitude, longitude, PricingRule.defaultRule());
		location.changeOpeningHours(opensAt, closesAt);
		return new LocationSummary(locationRepository.save(location), 0, 0);
	}

	@Transactional
	public LocationSummary update(UUID id, String name, String address, double latitude, double longitude,
			LocalTime opensAt, LocalTime closesAt) {
		ParkingLocation location = find(id);
		location.update(name, address, latitude, longitude);
		location.changeOpeningHours(opensAt, closesAt);
		return get(id);
	}

	/** A location can only be removed while nobody is parked on or holding one of its spots. */
	@Transactional
	public void delete(UUID id) {
		ParkingLocation location = find(id);
		// Name the spots in use, so the admin knows what to wait for (or which reserved spots to close first).
		List<String> inUse = spotRepository.findByLocationIdAndStatusIn(id, List.of(SpotStatus.RESERVED, SpotStatus.OCCUPIED))
			.stream().sorted(ParkingSpot.ORDER).map(ParkingSpot::getCode).toList();
		if (!inUse.isEmpty()) {
			throw new IllegalStateException(location.getName() + " can't be deleted while drivers use " + String.join(", ", inUse)
					+ ". Wait until those spots are free; reserved ones you can close first.");
		}
		spotRepository.deleteByLocationId(id);
		locationRepository.delete(location);
	}

	/** Strategy pattern: swapping the rule swaps the strategy used for every later session here. */
	@Transactional
	public LocationSummary updatePricingRule(UUID id, PricingRule rule) {
		find(id).changePricingRule(rule);
		return get(id);
	}

	/** How many spots are in each status, every status listed (zero included). */
	@Transactional(readOnly = true)
	public Map<SpotStatus, Long> availability(UUID id) {
		find(id);
		Map<SpotStatus, Long> result = new EnumMap<>(SpotStatus.class);
		for (SpotStatus status : SpotStatus.values()) {
			result.put(status, 0L);
		}
		spotRepository.countByStatus(id).forEach(c -> result.put(c.status(), c.count()));
		return result;
	}

	public ParkingLocation find(UUID id) {
		return locationRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Location " + id + " not found."));
	}

	private static LocationSummary summarize(ParkingLocation location, List<SpotCount> counts) {
		long total = counts.stream().mapToLong(SpotCount::count).sum();
		long free = counts.stream().filter(c -> c.status() == SpotStatus.AVAILABLE).mapToLong(SpotCount::count).sum();
		return new LocationSummary(location, total, free);
	}

}
