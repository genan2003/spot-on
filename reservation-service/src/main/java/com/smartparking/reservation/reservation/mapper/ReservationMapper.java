package com.smartparking.reservation.reservation.mapper;

import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.dto.ReservationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

	ReservationResponse toResponse(Reservation reservation);

}
