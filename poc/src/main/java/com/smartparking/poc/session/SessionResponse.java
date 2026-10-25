package com.smartparking.poc.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, UUID driverId, UUID spotId, String spotCode, SessionStatus status,
        Instant entryTime, Instant exitTime, BigDecimal cost) {

    static SessionResponse from(ParkingSession s) {
        return new SessionResponse(s.getId(), s.getDriver().getId(), s.getSpot().getId(), s.getSpot().getCode(),
                s.getStatus(), s.getEntryTime(), s.getExitTime(), s.getCost());
    }
}
