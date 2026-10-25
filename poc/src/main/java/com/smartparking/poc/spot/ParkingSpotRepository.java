package com.smartparking.poc.spot;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {
}
