package com.smartparking.parking.location.repository;

import com.smartparking.parking.location.domain.ParkingLocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, UUID> {

	List<ParkingLocation> findAllByOrderByNameAsc();

}
