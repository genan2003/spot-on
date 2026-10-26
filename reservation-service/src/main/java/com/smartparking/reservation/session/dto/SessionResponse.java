package com.smartparking.reservation.session.dto;

import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code cost} is a running estimate while the session is active and the final cost once it ends. */
public record SessionResponse(UUID id, UUID spotId, String spotCode, UUID locationId, String locationName,
		SessionStatus status, Instant startedAt, Instant endedAt, BigDecimal cost, UUID paymentId,
		PaymentStatus paymentStatus) {

	public SessionResponse withCost(BigDecimal estimate) {
		return new SessionResponse(id, spotId, spotCode, locationId, locationName, status, startedAt, endedAt,
				estimate, paymentId, paymentStatus);
	}

}
