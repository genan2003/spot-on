package com.smartparking.parking.spot.repository;

import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

	/** Ordered as on the ground: by level, zone, then number (A2 before A10). */
	@Query("""
			select s from ParkingSpot s where s.location.id = :locationId
			order by s.level, s.zone, length(s.code), s.code""")
	List<ParkingSpot> findByLocationIdOrdered(UUID locationId);

	/** Loads the location too, because hold responses include its name. */
	@EntityGraph(attributePaths = "location")
	Optional<ParkingSpot> findWithLocationById(UUID id);

	Optional<ParkingSpot> findByIdAndLocationId(UUID id, UUID locationId);

	long countByLocationIdAndLevelAndZone(UUID locationId, int level, String zone);

	boolean existsByLocationIdAndStatusIn(UUID locationId, Collection<SpotStatus> statuses);

	@Query("select new com.smartparking.parking.spot.repository.SpotCount(s.location.id, s.status, count(s)) "
			+ "from ParkingSpot s group by s.location.id, s.status")
	List<SpotCount> countByLocationAndStatus();

	@Query("select new com.smartparking.parking.spot.repository.SpotCount(s.location.id, s.status, count(s)) "
			+ "from ParkingSpot s where s.location.id = :locationId group by s.location.id, s.status")
	List<SpotCount> countByStatus(UUID locationId);

	@Modifying
	@Query("delete from ParkingSpot s where s.location.id = :locationId")
	void deleteByLocationId(UUID locationId);

}
