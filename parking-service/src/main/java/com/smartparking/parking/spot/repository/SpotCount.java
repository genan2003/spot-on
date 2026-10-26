package com.smartparking.parking.spot.repository;

import com.smartparking.parking.spot.domain.SpotStatus;
import java.util.UUID;

/** How many spots of a location are in one status. */
public record SpotCount(UUID locationId, SpotStatus status, long count) {
}
