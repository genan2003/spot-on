package com.smartparking.reservation.session.repository;

import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {

	boolean existsByDriverIdAndStatus(String driverId, SessionStatus status);

	Optional<ParkingSession> findFirstByDriverIdAndStatus(String driverId, SessionStatus status);

	/** History: the driver's newest sessions, running or ended. */
	List<ParkingSession> findTop50ByDriverIdOrderByStartedAtDesc(String driverId);

	Optional<ParkingSession> findFirstByDriverIdAndStatusOrderByEndedAtDesc(String driverId, SessionStatus status);

	/** Daily statistics: the car park's sessions that were running at some point between {@code from} and {@code to}. */
	@Query("""
			SELECT s FROM ParkingSession s
			WHERE s.locationId = :locationId AND s.startedAt < :to AND (s.endedAt IS NULL OR s.endedAt > :from)
			""")
	List<ParkingSession> findOverlapping(UUID locationId, Instant from, Instant to);

}
