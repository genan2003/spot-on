package com.smartparking.reservation.reservation.controller;

import com.smartparking.reservation.reservation.dto.ReservationResponse;
import com.smartparking.reservation.reservation.dto.ReserveRequest;
import com.smartparking.reservation.reservation.mapper.ReservationMapper;
import com.smartparking.reservation.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;
	private final ReservationMapper mapper;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
		return mapper.toResponse(reservationService.reserve(request.driverId(), request.spotId()));
	}

	@GetMapping("/{id}")
	public ReservationResponse get(@PathVariable UUID id) {
		return mapper.toResponse(reservationService.find(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable UUID id) {
		reservationService.cancel(id);
	}

}
