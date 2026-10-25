package com.smartparking.poc.session;

import com.smartparking.poc.reservation.Reservation;
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
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The time a car actually spends in a spot, from entry to exit, and what it cost. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(optional = false)
    @JoinColumn(name = "spot_id")
    private ParkingSpot spot;

    /** The reservation this session fulfils, if the driver reserved first. */
    @OneToOne
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    private Instant entryTime;

    private Instant exitTime;

    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;

    public ParkingSession(Driver driver, ParkingSpot spot, Reservation reservation, Instant entryTime) {
        this.driver = driver;
        this.spot = spot;
        this.reservation = reservation;
        this.entryTime = entryTime;
    }

    public void end(Instant exitTime, BigDecimal cost) {
        if (status == SessionStatus.COMPLETED) {
            throw new IllegalStateException("This parking session has already ended.");
        }
        this.exitTime = exitTime;
        this.cost = cost;
        this.status = SessionStatus.COMPLETED;
    }

    public Duration getDuration() {
        return Duration.between(entryTime, exitTime);
    }
}
