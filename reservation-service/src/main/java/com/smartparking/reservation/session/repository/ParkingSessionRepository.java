package com.smartparking.reservation.session.repository;

import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {

	boolean existsByDriverIdAndStatus(String driverId, SessionStatus status);

	Optional<ParkingSession> findFirstByDriverIdAndStatus(String driverId, SessionStatus status);

	/** History: the driver's newest sessions, running or ended. */
	List<ParkingSession> findTop50ByDriverIdOrderByStartedAtDesc(String driverId);

	Optional<ParkingSession> findFirstByDriverIdAndStatusOrderByEndedAtDesc(String driverId, SessionStatus status);

}
