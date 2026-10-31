package com.smartparking.poc.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByDriverIdAndSpotIdAndStatus(UUID driverId, UUID spotId, ReservationStatus status);

    Optional<Reservation> findBySpotIdAndStatus(UUID spotId, ReservationStatus status);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant time);
}
