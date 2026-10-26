package com.smartparking.parking.spot.mapper;

import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.dto.HeldSpotResponse;
import com.smartparking.parking.spot.dto.SpotResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SpotMapper {

	@Mapping(target = "locationId", source = "location.id")
	SpotResponse toResponse(ParkingSpot spot);

	@Mapping(target = "spotId", source = "id")
	@Mapping(target = "spotCode", source = "code")
	@Mapping(target = "locationId", source = "location.id")
	@Mapping(target = "locationName", source = "location.name")
	HeldSpotResponse toHeldResponse(ParkingSpot spot);

}
