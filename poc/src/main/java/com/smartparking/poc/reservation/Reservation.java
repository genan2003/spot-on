package com.smartparking.poc.reservation;

import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.user.Driver;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A driver's hold on a spot until {@link #expiresAt}. The database allows at most one ACTIVE
 * reservation per spot (see {@code V1__init.sql}), the second guard against double reservations.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(optional = false)
    @JoinColumn(name = "spot_id")
    private ParkingSpot spot;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    private Instant createdAt;

    private Instant expiresAt;

    public Reservation(Driver driver, ParkingSpot spot, Instant createdAt, Instant expiresAt) {
        this.driver = driver;
        this.spot = spot;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void cancel() {
        finish(ReservationStatus.CANCELLED);
    }

    public void expire() {
        finish(ReservationStatus.EXPIRED);
    }

    /** The driver started a session on the reserved spot. */
    public void complete() {
        finish(ReservationStatus.COMPLETED);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void finish(ReservationStatus newStatus) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("This reservation has already ended (" + status.name().toLowerCase() + ").");
        }
        status = newStatus;
    }
}
