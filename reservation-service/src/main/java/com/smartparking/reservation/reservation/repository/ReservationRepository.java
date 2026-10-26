package com.smartparking.reservation.reservation.repository;

import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

	boolean existsByDriverIdAndStatusIn(String driverId, Collection<ReservationStatus> statuses);

	Optional<Reservation> findFirstByDriverIdAndStatus(String driverId, ReservationStatus status);

	Optional<Reservation> findByDriverIdAndSpotIdAndStatus(String driverId, UUID spotId, ReservationStatus status);

	List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant time);

}
