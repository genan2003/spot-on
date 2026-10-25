package com.smartparking.poc.reservation;

import com.smartparking.poc.event.ReservationCancelledEvent;
import com.smartparking.poc.event.ReservationCreatedEvent;
import com.smartparking.poc.event.ReservationExpiredEvent;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.user.Driver;
import com.smartparking.poc.user.DriverRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves, cancels and expires reservations. Spot rules come from the spot's state
 * (<b>State pattern</b>); every change is announced as a domain event (<b>Observer pattern:
 * Publisher</b>) and this class never calls the notification code directly.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository spotRepository;
    private final DriverRepository driverRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration reservationDuration;

    public ReservationService(ReservationRepository reservationRepository, ParkingSpotRepository spotRepository,
            DriverRepository driverRepository, ApplicationEventPublisher eventPublisher, Clock clock,
            @Value("${smartparking.reservation.duration}") Duration reservationDuration) {
        this.reservationRepository = reservationRepository;
        this.spotRepository = spotRepository;
        this.driverRepository = driverRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.reservationDuration = reservationDuration;
    }

    /**
     * Reserves a free spot. A spot that isn't free is rejected by its state. If two requests for
     * the same spot run at once, the spot's {@code @Version} check or the unique index on active
     * reservations makes the slower commit fail, and the caller gets 409 Conflict.
     */
    @Transactional
    public Reservation reserve(UUID driverId, UUID spotId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver " + driverId + " not found."));
        ParkingSpot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot " + spotId + " not found."));

        spot.reserve();

        Instant now = clock.instant();
        Reservation reservation = reservationRepository.save(new Reservation(driver, spot, now, now.plus(reservationDuration)));
        eventPublisher.publishEvent(new ReservationCreatedEvent(now, reservation.getId(), driverId, spotId, spot.getCode()));
        return reservation;
    }

    @Transactional
    public void cancel(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation " + reservationId + " not found."));
        reservation.cancel();
        reservation.getSpot().release();
        eventPublisher.publishEvent(new ReservationCancelledEvent(clock.instant(), reservationId,
                reservation.getDriver().getId(), reservation.getSpot().getId(), reservation.getSpot().getCode()));
    }

    /** Expires every active reservation whose hold time has passed and frees its spot. */
    @Transactional
    public int expireOverdue(Instant now) {
        List<Reservation> overdue = reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, now);
        for (Reservation reservation : overdue) {
            reservation.expire();
            reservation.getSpot().release();
            eventPublisher.publishEvent(new ReservationExpiredEvent(now, reservation.getId(),
                    reservation.getDriver().getId(), reservation.getSpot().getId(), reservation.getSpot().getCode()));
        }
        return overdue.size();
    }
}
