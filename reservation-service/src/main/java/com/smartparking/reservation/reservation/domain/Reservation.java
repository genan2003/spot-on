package com.smartparking.reservation.reservation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Duration;
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

	/** A reservation can be extended once; then the driver has to start the session or let it go. */
	private boolean extended;

	/**
	 * Optimistic locking: a move, an extension, a cancel and the expiry timer can hit the same row at once.
	 * The slower one fails instead of overwriting the other (e.g. bringing an expired reservation back).
	 */
	@Version
	private long version;

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

	/**
	 * Moves the hold to another spot; the hold time is not reset. parking-service has already
	 * reserved the new spot for this reservation's id.
	 */
	public void moveTo(UUID newSpotId, String newSpotCode, UUID newLocationId, String newLocationName, Instant now) {
		requireChangeable(now);
		if (newSpotId.equals(spotId)) {
			throw new IllegalStateException("Your reservation is already for " + spotCode + ".");
		}
		this.spotId = newSpotId;
		this.spotCode = newSpotCode;
		this.locationId = newLocationId;
		this.locationName = newLocationName;
	}

	/** Holds the spot {@code by} longer. Allowed once per reservation. */
	public void extend(Duration by, Instant now) {
		requireChangeable(now);
		if (extended) {
			throw new IllegalStateException("You have already extended this reservation once.");
		}
		expiresAt = expiresAt.plus(by);
		extended = true;
	}

	/** Only an active reservation whose hold time hasn't run out can be moved or extended. */
	public void requireChangeable(Instant now) {
		if (!isActive()) {
			throw new IllegalStateException("This reservation has already ended (" + status.name().toLowerCase() + ").");
		}
		if (isOverdue(now)) {
			throw new IllegalStateException("This reservation's hold time is over.");
		}
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
