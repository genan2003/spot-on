package com.smartparking.reservation.session.mapper;

import com.smartparking.reservation.session.domain.ParkingSession;
import com.smartparking.reservation.session.dto.SessionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SessionMapper {

	SessionResponse toResponse(ParkingSession session);

}
