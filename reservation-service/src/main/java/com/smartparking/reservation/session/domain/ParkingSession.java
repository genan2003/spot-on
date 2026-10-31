package com.smartparking.reservation.session.domain;

import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.pricing.BayType;
import com.smartparking.reservation.reservation.domain.Reservation;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The time a car actually spends in a spot, from entry to exit, what it cost, and whether it was paid. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSession {

	/** Assigned before saving, because parking-service records it as the spot's hold before this row exists. */
	@Id
	private UUID id;

	private String driverId;

	private UUID spotId;

	private String spotCode;

	private UUID locationId;

	private String locationName;

	/** The bay's type and charger, copied at entry: the pricing rule's surcharge depends on them. */
	@Enumerated(EnumType.STRING)
	private BayType spotType = BayType.STANDARD;

	private boolean evCharging;

	/** The reservation this session fulfils, if the driver reserved first. */
	@OneToOne
	@JoinColumn(name = "reservation_id")
	private Reservation reservation;

	@Enumerated(EnumType.STRING)
	private SessionStatus status = SessionStatus.ACTIVE;

	private Instant startedAt;

	private Instant endedAt;

	private BigDecimal cost;

	private UUID paymentId;

	@Enumerated(EnumType.STRING)
	private PaymentStatus paymentStatus;

	public ParkingSession(UUID id, String driverId, UUID spotId, String spotCode, UUID locationId, String locationName,
			Bay bay, Reservation reservation, Instant startedAt) {
		this.id = id;
		this.driverId = driverId;
		this.spotId = spotId;
		this.spotCode = spotCode;
		this.locationId = locationId;
		this.locationName = locationName;
		this.spotType = bay.type();
		this.evCharging = bay.evCharging();
		this.reservation = reservation;
		this.startedAt = startedAt;
	}

	public Bay getBay() {
		return new Bay(spotType, evCharging);
	}

	public boolean isActive() {
		return status == SessionStatus.ACTIVE;
	}

	/** Records the exit and the final cost; payment follows asynchronously, so it starts as PENDING. */
	public void end(Instant endedAt, BigDecimal cost) {
		if (!isActive()) {
			throw new IllegalStateException("This parking session has already ended.");
		}
		this.endedAt = endedAt;
		this.cost = cost;
		this.status = SessionStatus.COMPLETED;
		this.paymentStatus = PaymentStatus.PENDING;
	}

	/** Called when payment-service reports the outcome (payment.completed / payment.failed). */
	public void recordPayment(UUID paymentId, PaymentStatus paymentStatus) {
		this.paymentId = paymentId;
		this.paymentStatus = paymentStatus;
	}

}
