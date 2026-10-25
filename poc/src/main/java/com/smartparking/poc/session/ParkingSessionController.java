package com.smartparking.poc.session;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** <b>Facade pattern: Client.</b> One facade call per request, nothing else. */
@RestController
@RequestMapping("/api/v1/parking-sessions")
@RequiredArgsConstructor
public class ParkingSessionController {

    private final ParkingSessionFacade parkingSessionFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse start(@Valid @RequestBody StartSessionRequest request) {
        return SessionResponse.from(parkingSessionFacade.startSession(request.driverId(), request.spotId()));
    }

    @PostMapping("/{id}/end")
    public SessionResponse end(@PathVariable UUID id) {
        return SessionResponse.from(parkingSessionFacade.endSession(id));
    }
}
