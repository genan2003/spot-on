package com.smartparking.poc.location;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, UUID> {
}
