package com.smartparking.parking.spot.service;

import com.smartparking.parking.common.TimeConfig;
import com.smartparking.parking.location.domain.ParkingLocation;
import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.event.SpotStatusChanged;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver-side spot changes. Reserve and occupy are synchronous calls from reservation-service, because
 * the driver needs the answer now. Release comes from events (reservation cancelled or expired, session
 * completed) and, to undo a failed step, from a synchronous call.
 * This is the first guard against double reservations: the spot's state rejects a spot that isn't
 * free (<b>State pattern</b>), and its {@code @Version} rejects the slower of two simultaneous
 * requests at commit. Both end as {@code 409 Conflict}.
 */
@Service
@RequiredArgsConstructor
public class SpotHoldService {

	private final ParkingSpotRepository spotRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Transactional
	public ParkingSpot reserve(UUID spotId, String driverId, UUID reservationId) {
		return change(spotId, spot -> {
			requireOpen(spot);
			spot.reserve(driverId, reservationId);
		});
	}

	@Transactional
	public ParkingSpot occupy(UUID spotId, String driverId, UUID sessionId, String licencePlate) {
		return change(spotId, spot -> {
			requireOpen(spot);
			spot.occupy(driverId, sessionId, licencePlate);
		});
	}

	@Transactional
	public ParkingSpot release(UUID spotId, UUID holdId) {
		return change(spotId, spot -> spot.release(holdId));
	}

	/**
	 * Release for an event: if the spot has moved on to another hold (or is already free), the event
	 * is stale and there is nothing to do. Returns whether the spot was freed.
	 */
	@Transactional
	public boolean releaseIfHeld(UUID spotId, UUID holdId) {
		return spotRepository.findWithLocationById(spotId)
			.filter(spot -> spot.isHeldBy(holdId))
			.map(spot -> {
				spot.release(holdId);
				eventPublisher.publishEvent(SpotStatusChanged.of(spot));
				return true;
			})
			.orElse(false);
	}

	/**
	 * Reserving (and moving a reservation) and starting a session need the car park to be open now, in its local time.
	 * Ending a session and releasing a spot never do: a car must always be able to leave. 409, like a taken spot.
	 */
	private void requireOpen(ParkingSpot spot) {
		ParkingLocation location = spot.getLocation();
		if (!location.isOpenAt(LocalTime.now(clock.withZone(TimeConfig.CAR_PARK_ZONE)))) {
			throw new IllegalStateException("%s is closed now. It opens at %s.".formatted(location.getName(), location.getOpensAt()));
		}
	}

	private ParkingSpot change(UUID spotId, Consumer<ParkingSpot> action) {
		ParkingSpot spot = spotRepository.findWithLocationById(spotId)
			.orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found."));
		action.accept(spot);
		// Observer pattern: listeners (live spot plan) run after the commit.
		eventPublisher.publishEvent(SpotStatusChanged.of(spot));
		return spot;
	}

}
