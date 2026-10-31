package com.smartparking.reservation.reservation.service;

import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.event.ReservationCancelledEvent;
import com.smartparking.reservation.event.ReservationChangedEvent;
import com.smartparking.reservation.event.ReservationCreatedEvent;
import com.smartparking.reservation.event.ReservationExpiredEvent;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.SessionStatus;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Reserves, cancels and expires reservations. Reserving needs parking-service's answer now, so the
 * spot is held with a synchronous call. Everything after a change is announced as an event
 * (<b>Observer pattern: Publisher</b>): parking-service frees the spot and notification-service tells
 * the driver, without this class knowing either of them.
 */
@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final ParkingSessionRepository sessionRepository;
	private final ParkingClient parkingClient;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;
	private final Duration reservationDuration;

	public ReservationService(ReservationRepository reservationRepository, ParkingSessionRepository sessionRepository,
			ParkingClient parkingClient, ApplicationEventPublisher eventPublisher, Clock clock,
			@Value("${smartparking.reservation.duration}") Duration reservationDuration) {
		this.reservationRepository = reservationRepository;
		this.sessionRepository = sessionRepository;
		this.parkingClient = parkingClient;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
		this.reservationDuration = reservationDuration;
	}

	/**
	 * 1. parking-service holds the spot for this reservation's id; its state and {@code @Version} refuse
	 *    a spot that isn't free (409).
	 * 2. The reservation is saved; the unique indexes refuse a second open reservation for the spot or driver.
	 *    If this fails, step 1 is undone (compensating release), so the spot isn't left reserved for nobody.
	 * 3. {@code reservation.created} is published; it also starts the expiry timer.
	 */
	public Reservation reserve(String driverId, UUID spotId) {
		if (reservationRepository.existsByDriverIdAndStatusIn(driverId, ReservationStatus.OPEN)
				|| sessionRepository.existsByDriverIdAndStatus(driverId, SessionStatus.ACTIVE)) {
			throw new IllegalStateException("You already have a reservation or a session running.");
		}

		UUID reservationId = UUID.randomUUID();
		HeldSpot spot = parkingClient.reserveSpot(spotId, driverId, reservationId);

		Instant now = clock.instant();
		Reservation reservation;
		try {
			reservation = reservationRepository.saveAndFlush(new Reservation(reservationId, driverId, spotId, spot.spotCode(),
					spot.locationId(), spot.locationName(), now, now.plus(reservationDuration)));
		}
		catch (DataIntegrityViolationException ex) {
			parkingClient.releaseSpotIfHeld(spotId, reservationId);
			throw ex;
		}
		eventPublisher.publishEvent(ReservationCreatedEvent.of(reservation, now));
		return reservation;
	}

	/**
	 * Moves a reservation to another free spot, keeping its hold time. Same order as {@link #reserve}:
	 * <ol>
	 *   <li>parking-service holds the new spot for this reservation's id (409 if it isn't free),</li>
	 *   <li>the reservation row points to the new spot; the unique index refuses a spot someone else
	 *       reserved meanwhile, and {@code @Version} a reservation that changed meanwhile (cancelled,
	 *       expired). If this fails, the new spot is released again (compensation),</li>
	 *   <li>{@code reservation.changed} is published; parking-service frees the previous spot.</li>
	 * </ol>
	 */
	public Reservation move(UUID reservationId, UUID newSpotId) {
		Reservation reservation = find(reservationId);
		Instant now = clock.instant();
		reservation.requireChangeable(now);
		if (newSpotId.equals(reservation.getSpotId())) {
			throw new IllegalStateException("Your reservation is already for " + reservation.getSpotCode() + ".");
		}
		UUID previousSpotId = reservation.getSpotId();
		String previousSpotCode = reservation.getSpotCode();

		HeldSpot spot = parkingClient.reserveSpot(newSpotId, reservation.getDriverId(), reservationId);
		Reservation moved;
		try {
			reservation.moveTo(newSpotId, spot.spotCode(), spot.locationId(), spot.locationName(), now);
			moved = reservationRepository.saveAndFlush(reservation);
		}
		catch (DataIntegrityViolationException | ConcurrencyFailureException | IllegalStateException ex) {
			parkingClient.releaseSpotIfHeld(newSpotId, reservationId);
			throw ex;
		}
		eventPublisher.publishEvent(ReservationChangedEvent.moved(moved, previousSpotId, previousSpotCode, now));
		return moved;
	}

	/** Holds the spot one more hold time (once). The expiry timer picks up the new time when its first check fires. */
	public Reservation extend(UUID reservationId) {
		Reservation reservation = find(reservationId);
		Instant now = clock.instant();
		reservation.extend(reservationDuration, now);
		Reservation extended = reservationRepository.saveAndFlush(reservation);
		eventPublisher.publishEvent(ReservationChangedEvent.extended(extended, now));
		return extended;
	}

	/** parking-service frees the spot when it receives {@code reservation.cancelled}. */
	public void cancel(UUID reservationId) {
		Reservation reservation = find(reservationId);
		if (!reservation.isActive()) {
			throw new IllegalStateException("This reservation has already ended.");
		}
		reservation.cancel();
		reservationRepository.save(reservation);
		eventPublisher.publishEvent(ReservationCancelledEvent.of(reservation, clock.instant()));
	}

	/** Called when the reservation's expiry timer fires. Does nothing if it was used, cancelled or already expired. */
	public boolean expireIfDue(UUID reservationId, Instant now) {
		return reservationRepository.findById(reservationId)
			.filter(reservation -> reservation.isOverdue(now))
			.map(reservation -> {
				expire(reservation, now);
				return true;
			})
			.orElse(false);
	}

	/** When an active reservation's hold ends; empty if it has ended already. */
	public Optional<Instant> activeUntil(UUID reservationId) {
		return reservationRepository.findById(reservationId).filter(Reservation::isActive).map(Reservation::getExpiresAt);
	}

	/** Fallback sweep: expires every active reservation whose hold time has passed. */
	public int expireOverdue(Instant now) {
		List<Reservation> overdue = reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);
		overdue.forEach(reservation -> expire(reservation, now));
		return overdue.size();
	}

	public Reservation find(UUID reservationId) {
		return reservationRepository.findById(reservationId)
			.orElseThrow(() -> new EntityNotFoundException("Reservation " + reservationId + " not found."));
	}

	/** parking-service frees the spot when it receives {@code reservation.expired}. */
	private void expire(Reservation reservation, Instant now) {
		reservation.expire();
		reservationRepository.save(reservation);
		eventPublisher.publishEvent(ReservationExpiredEvent.of(reservation, now));
	}

}
