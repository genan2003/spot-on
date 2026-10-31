package com.smartparking.poc.location;

import com.smartparking.poc.event.ReservationCancelledEvent;
import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.reservation.Reservation;
import com.smartparking.poc.reservation.ReservationRepository;
import com.smartparking.poc.reservation.ReservationStatus;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
import com.smartparking.poc.spot.SpotType;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin operations on locations, spots and pricing, plus the availability summary drivers see. */
@Service
@RequiredArgsConstructor
public class ParkingLocationService {

    private final ParkingLocationRepository locationRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ParkingLocation createLocation(String name, String address, double latitude, double longitude, PricingRule rule) {
        return locationRepository.save(new ParkingLocation(name, address, latitude, longitude, rule));
    }

    /** Adds {@code count} spots to a zone, numbered after the ones already there (A1, A2, ...). */
    @Transactional
    public List<ParkingSpot> addSpots(UUID locationId, int level, String zone, int count, SpotType type,
            boolean evCharging, boolean accessible) {
        ParkingLocation location = findLocation(locationId);
        long existing = location.getSpots().stream().filter(s -> s.getLevel() == level && s.getZone().equals(zone)).count();
        List<ParkingSpot> added = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ParkingSpot spot = new ParkingSpot(zone + (existing + i), level, zone, type, evCharging, accessible);
            location.addSpot(spot);
            added.add(spot);
        }
        locationRepository.flush();
        return added;
    }

    /** Strategy pattern: swapping the rule swaps the strategy used for every later session here. */
    @Transactional
    public void updatePricingRule(UUID locationId, PricingRule rule) {
        rule.toStrategy(); // fails fast if a rate the strategy needs is missing
        findLocation(locationId).changePricingRule(rule);
    }

    @Transactional
    public ParkingSpot setOutOfService(UUID spotId, boolean outOfService) {
        ParkingSpot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found."));
        if (outOfService) {
            // State: a reserved spot can be closed (there is no car in it yet); its reservation is cancelled
            // in the same transaction and the driver is told (Observer). An occupied spot is still refused.
            Optional<Reservation> reservation = spot.getStatus() == SpotStatus.RESERVED
                    ? reservationRepository.findBySpotIdAndStatus(spotId, ReservationStatus.ACTIVE)
                    : Optional.empty();
            spot.markOutOfService();
            reservation.ifPresent(r -> {
                r.cancel();
                eventPublisher.publishEvent(new ReservationCancelledEvent(clock.instant(), r.getId(), r.getDriver().getId(),
                        spotId, spot.getCode(), true));
            });
        } else {
            spot.returnToService();
        }
        return spot;
    }

    @Transactional(readOnly = true)
    public Map<SpotStatus, Long> getAvailability(UUID locationId) {
        Map<SpotStatus, Long> counts = new EnumMap<>(SpotStatus.class);
        for (SpotStatus status : SpotStatus.values()) {
            counts.put(status, 0L);
        }
        for (ParkingSpot spot : findLocation(locationId).getSpots()) {
            counts.merge(spot.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    private ParkingLocation findLocation(UUID locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException("Location " + locationId + " not found."));
    }
}
