package com.smartparking.poc.session;

import com.smartparking.poc.event.PaymentCompletedEvent;
import com.smartparking.poc.event.PaymentFailedEvent;
import com.smartparking.poc.event.SessionCompletedEvent;
import com.smartparking.poc.payment.Payment;
import com.smartparking.poc.payment.PaymentProcessorFactory;
import com.smartparking.poc.payment.PaymentRepository;
import com.smartparking.poc.payment.PaymentResult;
import com.smartparking.poc.pricing.PricingStrategy;
import com.smartparking.poc.reservation.Reservation;
import com.smartparking.poc.reservation.ReservationRepository;
import com.smartparking.poc.reservation.ReservationStatus;
import com.smartparking.poc.spot.IllegalSpotStateTransitionException;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
import com.smartparking.poc.user.Driver;
import com.smartparking.poc.user.DriverRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Facade pattern: Facade.</b> The one entry point for starting and ending a parking session.
 * Ending a session touches five subsystems in a fixed order, inside one transaction:
 * <ol>
 *   <li>record the exit time on the session,</li>
 *   <li>calculate the cost with the location's {@link PricingStrategy} (<b>Strategy</b>),</li>
 *   <li>pay with the processor from {@link PaymentProcessorFactory} (<b>Factory</b>),</li>
 *   <li>release the spot, Occupied → Available (<b>State</b>),</li>
 *   <li>publish the session and payment events (<b>Observer</b>).</li>
 * </ol>
 * The controller makes a single call and never sees these steps.
 */
@Service
@RequiredArgsConstructor
public class ParkingSessionFacade {

    private final ParkingSessionRepository sessionRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final DriverRepository driverRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProcessorFactory paymentProcessorFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Starts a session on a spot that is free, or reserved by this driver. A spot reserved by
     * someone else is refused here; an occupied or closed spot is refused by its state.
     */
    @Transactional
    public ParkingSession startSession(UUID driverId, UUID spotId) {
        ParkingSpot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found."));
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver " + driverId + " not found."));
        Reservation reservation = reservationRepository
                .findByDriverIdAndSpotIdAndStatus(driverId, spotId, ReservationStatus.ACTIVE)
                .orElse(null);

        if (spot.getStatus() == SpotStatus.RESERVED && reservation == null) {
            throw new IllegalSpotStateTransitionException("Spot " + spot.getCode() + " is reserved by another driver.");
        }

        spot.occupy();
        if (reservation != null) {
            reservation.complete();
        }
        return sessionRepository.save(new ParkingSession(driver, spot, reservation, clock.instant()));
    }

    @Transactional
    public ParkingSession endSession(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session " + sessionId + " not found."));
        ParkingSpot spot = session.getSpot();

        // 1. Record the exit time.
        Instant exitTime = clock.instant();

        // 2. Strategy: the location's pricing rule decides the cost.
        PricingStrategy strategy = spot.getLocation().getPricingStrategy();
        BigDecimal cost = strategy.calculateCost(session.getEntryTime(), exitTime);
        session.end(exitTime, cost);

        // 3. Factory: pay with the driver's preferred method.
        Payment payment = new Payment(session, cost, session.getDriver().getPreferredPaymentMethod());
        PaymentResult result = paymentProcessorFactory.create(payment.getMethod()).process(payment);
        if (result.success()) {
            payment.markCompleted(exitTime);
        } else {
            // The car has left either way; the spot is freed and the driver can pay again later.
            payment.markFailed(exitTime);
        }
        paymentRepository.save(payment);

        // 4. State: Occupied → Available.
        spot.release();
        sessionRepository.save(session);

        // 5. Observer: listeners are notified after the commit.
        UUID driverId = session.getDriver().getId();
        eventPublisher.publishEvent(new SessionCompletedEvent(exitTime, sessionId, driverId, spot.getCode(), cost));
        eventPublisher.publishEvent(result.success()
                ? new PaymentCompletedEvent(exitTime, payment.getId(), sessionId, driverId, cost)
                : new PaymentFailedEvent(exitTime, payment.getId(), sessionId, driverId, cost));
        return session;
    }
}
