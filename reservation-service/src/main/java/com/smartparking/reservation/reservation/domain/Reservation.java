package com.smartparking.reservation.reservation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A driver's hold on a spot until {@link #expiresAt}. The database allows at most one open
 * reservation per spot and per driver (partial unique indexes in {@code V1__init.sql}): the second
 * guard against double reservations, after parking-service's spot state and {@code @Version}.
 * Spot and location live in parking-service; their code and name are copied here for display.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

	/** Assigned before saving, because parking-service records it as the spot's hold before this row exists. */
	@Id
	private UUID id;

	private String driverId;

	private UUID spotId;

	private String spotCode;

	private UUID locationId;

	private String locationName;

	@Enumerated(EnumType.STRING)
	private ReservationStatus status = ReservationStatus.ACTIVE;

	private Instant createdAt;

	private Instant expiresAt;

	public Reservation(UUID id, String driverId, UUID spotId, String spotCode, UUID locationId, String locationName,
			Instant createdAt, Instant expiresAt) {
		this.id = id;
		this.driverId = driverId;
		this.spotId = spotId;
		this.spotCode = spotCode;
		this.locationId = locationId;
		this.locationName = locationName;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isActive() {
		return status == ReservationStatus.ACTIVE;
	}

	public boolean isOverdue(Instant now) {
		return isActive() && !now.isBefore(expiresAt);
	}

	public void cancel() {
		finish(ReservationStatus.CANCELLED);
	}

	public void expire() {
		finish(ReservationStatus.EXPIRED);
	}

	/** The driver started a session on the reserved spot. */
	public void complete() {
		finish(ReservationStatus.COMPLETED);
	}

	private void finish(ReservationStatus newStatus) {
		if (!isActive()) {
			throw new IllegalStateException("This reservation has already ended (" + status.name().toLowerCase() + ").");
		}
		status = newStatus;
	}

}
