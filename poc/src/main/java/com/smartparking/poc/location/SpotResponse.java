package com.smartparking.poc.location;

import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.SpotStatus;
import com.smartparking.poc.spot.SpotType;
import java.util.UUID;

public record SpotResponse(UUID id, String code, int level, String zone, SpotType type, boolean evCharging,
        boolean accessible, SpotStatus status) {

    static SpotResponse from(ParkingSpot s) {
        return new SpotResponse(s.getId(), s.getCode(), s.getLevel(), s.getZone(), s.getType(), s.isEvCharging(),
                s.isAccessible(), s.getStatus());
    }
}
