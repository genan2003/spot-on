package com.smartparking.reservation.session.repository;

import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.SessionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {

	boolean existsByDriverIdAndStatus(String driverId, SessionStatus status);

	Optional<ParkingSession> findFirstByDriverIdAndStatus(String driverId, SessionStatus status);

	Optional<ParkingSession> findFirstByDriverIdAndStatusOrderByEndedAtDesc(String driverId, SessionStatus status);

}
