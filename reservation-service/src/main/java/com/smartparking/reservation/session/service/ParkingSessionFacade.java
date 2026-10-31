package com.smartparking.reservation.session.service;

import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.event.SessionCompletedEvent;
import com.smartparking.reservation.event.SessionStartedEvent;
import com.smartparking.reservation.pricing.PricingStrategy;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.domain.SessionStatus;
import com.smartparking.reservation.session.repository.ParkingSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Facade pattern: Facade.</b> The one entry point for starting and ending a parking session.
 * Ending a session touches several subsystems in a fixed order:
 * <ol>
 *   <li>record the exit time on the session,</li>
 *   <li>fetch the location's pricing rule from parking-service and calculate the cost with its
 *       {@link PricingStrategy} (<b>Strategy</b>), wrapped in the rule's extras such as a grace period
 *       or a daily maximum (<b>Decorator</b>),</li>
 *   <li>publish {@code session.completed} (<b>Observer</b>, over RabbitMQ): payment-service charges
 *       the driver (<b>Factory</b> picks the processor), parking-service frees the spot, Occupied →
 *       Available (<b>State</b>), and notification-service tells the driver.</li>
 * </ol>
 * Compared with the PoC, payment and spot release move out of the facade's transaction: the
 * session ends immediately and the rest follows a moment later, even if a service is briefly down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingSessionFacade {

	private final ParkingSessionRepository sessionRepository;
	private final ReservationRepository reservationRepository;
	private final ParkingClient parkingClient;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * Starts a session on a spot that is free, or reserved by this driver. parking-service decides
	 * (State pattern); a spot reserved by someone else, occupied or closed is refused with 409.
	 */
	public ParkingSession startSession(String driverId, UUID spotId, UUID reservationId) {
		if (sessionRepository.existsByDriverIdAndStatus(driverId, SessionStatus.ACTIVE)) {
			throw new IllegalStateException("You already have a session running.");
		}
		Reservation reservation = findOwnReservation(driverId, spotId, reservationId);

		UUID sessionId = UUID.randomUUID();
		HeldSpot spot = parkingClient.occupySpot(spotId, driverId, sessionId);

		ParkingSession session;
		try {
			session = transactionTemplate.execute(tx -> {
				if (reservation != null) {
					reservation.complete();
					reservationRepository.save(reservation);
				}
				return sessionRepository.saveAndFlush(new ParkingSession(sessionId, driverId, spotId, spot.spotCode(),
						spot.locationId(), spot.locationName(), spot.bay(), reservation, clock.instant()));
			});
		}
		catch (DataIntegrityViolationException | IllegalStateException ex) {
			// Saving failed after the spot was occupied (a second session, or the reservation ended
			// meanwhile): undo the occupy so the spot isn't left blocked by a session that doesn't exist.
			parkingClient.releaseSpotIfHeld(spotId, sessionId);
			throw ex;
		}
		eventPublisher.publishEvent(SessionStartedEvent.of(session, clock.instant()));
		return session;
	}

	public ParkingSession endSession(UUID sessionId) {
		ParkingSession session = find(sessionId);
		if (!session.isActive()) {
			throw new IllegalStateException("This parking session has already ended.");
		}

		// 1. Record the exit time.
		Instant exitTime = clock.instant();

		// 2. Strategy + Decorator: the location's pricing rule and its extras decide the cost.
		BigDecimal cost = calculateCost(session, exitTime);
		session.end(exitTime, cost);
		sessionRepository.save(session);

		// 3. Observer: payment, spot release (State) and notification react to this event.
		eventPublisher.publishEvent(SessionCompletedEvent.of(session, exitTime));
		log.info("Session {} ended at {}, cost {} lei", sessionId, session.getSpotCode(), cost);
		return session;
	}

	/** Stores payment-service's outcome (from payment.completed / payment.failed) on the session. */
	@Transactional
	public void recordPayment(UUID sessionId, UUID paymentId, PaymentStatus status) {
		find(sessionId).recordPayment(paymentId, status);
	}

	/** Cost so far for a running session, or the final cost of a finished one. */
	public BigDecimal calculateCost(ParkingSession session, Instant until) {
		PricingStrategy strategy = parkingClient.getLocation(session.getLocationId()).pricing().toStrategy(session.getBay());
		return strategy.calculateCost(session.getStartedAt(), until);
	}

	public ParkingSession find(UUID sessionId) {
		return sessionRepository.findById(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("Session " + sessionId + " not found."));
	}

	/** The reservation the driver is using, if any: the one named in the request, or their reservation for this spot. */
	private Reservation findOwnReservation(String driverId, UUID spotId, UUID reservationId) {
		if (reservationId == null) {
			return reservationRepository.findByDriverIdAndSpotIdAndStatus(driverId, spotId, ReservationStatus.ACTIVE)
				.orElse(null);
		}
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new EntityNotFoundException("Reservation " + reservationId + " not found."));
		if (!reservation.getDriverId().equals(driverId) || !reservation.getSpotId().equals(spotId)) {
			throw new IllegalStateException("This reservation is for another spot or driver.");
		}
		if (!reservation.isActive()) {
			throw new IllegalStateException("This reservation has already ended.");
		}
		return reservation;
	}

}
