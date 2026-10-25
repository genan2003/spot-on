package com.smartparking.poc.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
}
