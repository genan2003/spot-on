package com.smartparking.parking.spot.dto;

import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import java.util.UUID;

public record SpotResponse(UUID id, UUID locationId, String code, int level, String zone, SpotType type,
		boolean evCharging, boolean accessible, SpotStatus status, String heldBy) {
}
