package com.smartparking.reservation.session.controller;

import com.smartparking.reservation.session.dto.SessionResponse;
import com.smartparking.reservation.session.dto.StartSessionRequest;
import com.smartparking.reservation.session.mapper.SessionMapper;
import com.smartparking.reservation.session.service.ParkingSessionFacade;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Talks only to the facade, never to the subsystems behind it. */
@RestController
@RequestMapping("/api/v1/parking-sessions")
@RequiredArgsConstructor
public class ParkingSessionController {

	private final ParkingSessionFacade sessionFacade;
	private final SessionMapper mapper;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionResponse start(@Valid @RequestBody StartSessionRequest request) {
		return mapper.toResponse(sessionFacade.startSession(request.driverId(), request.spotId(), request.reservationId(),
				request.licencePlate()));
	}

	/** The driver's sessions, newest first (at most 50). */
	@GetMapping(params = "driverId")
	public List<SessionResponse> history(@RequestParam String driverId) {
		return sessionFacade.history(driverId).stream().map(mapper::toResponse).toList();
	}

	@GetMapping("/{id}")
	public SessionResponse get(@PathVariable UUID id) {
		return mapper.toResponse(sessionFacade.find(id));
	}

	@PostMapping("/{id}/end")
	public SessionResponse end(@PathVariable UUID id) {
		return mapper.toResponse(sessionFacade.endSession(id));
	}

}
