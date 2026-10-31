package com.smartparking.reservation.reservation.dto;

import com.smartparking.reservation.reservation.domain.CancelReason;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(UUID id, UUID spotId, String spotCode, UUID locationId, String locationName,
		ReservationStatus status, Instant createdAt, Instant expiresAt, boolean extended,
		CancelReason cancelReason, Instant endedAt) {
}
