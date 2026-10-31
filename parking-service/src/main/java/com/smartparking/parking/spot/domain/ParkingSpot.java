package com.smartparking.parking.spot.domain;

import com.smartparking.parking.location.domain.ParkingLocation;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.util.Comparator;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <b>State pattern: Context.</b> A spot delegates every lifecycle action to its current
 * {@link SpotState}. Only {@link #status} is stored; the state object is rebuilt from it on load.
 *
 * <p>{@link #version} gives optimistic locking: if two transactions change the same spot, the
 * second commit fails, so two drivers can never both reserve it.
 *
 * <p>Unlike the PoC, the spot also remembers who holds it: the driver ({@link #heldBy}) and the
 * reservation or session ({@link #holdId}). Reservations live in another service, so parking-service
 * checks the holder itself, and release events must name the hold they end.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpot {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "location_id")
	private ParkingLocation location;

	private String code;

	private int level;

	private String zone;

	@Enumerated(EnumType.STRING)
	private SpotType type;

	private boolean evCharging;

	private boolean accessible;

	@Enumerated(EnumType.STRING)
	private SpotStatus status = SpotStatus.AVAILABLE;

	/** Driver holding the spot while it is reserved or occupied, {@code null} otherwise. */
	private String heldBy;

	/** Id of the reservation or session holding the spot, {@code null} otherwise. */
	private UUID holdId;

	@Transient
	private SpotState state = new AvailableState();

	@Version
	private long version;

	/** Level, zone, then number (A2 before A10), the order spot lists use everywhere. */
	public static final Comparator<ParkingSpot> ORDER = Comparator.comparingInt(ParkingSpot::getLevel)
		.thenComparing(ParkingSpot::getZone)
		.thenComparingInt(ParkingSpot::getNumber);

	public ParkingSpot(ParkingLocation location, String code, int level, String zone, SpotType type,
			boolean evCharging, boolean accessible) {
		this.location = location;
		this.code = code;
		this.level = level;
		this.zone = zone;
		this.type = type;
		this.evCharging = evCharging;
		this.accessible = accessible;
	}

	/**
	 * Admin edit of the spot's properties; {@code null} keeps a property as it is. Allowed in any state:
	 * a running session keeps the bay it was priced with (reservation-service copied it at entry).
	 */
	public void changeProperties(SpotType newType, Boolean newEvCharging, Boolean newAccessible) {
		if (newType != null) {
			type = newType;
		}
		if (newEvCharging != null) {
			evCharging = newEvCharging;
		}
		if (newAccessible != null) {
			accessible = newAccessible;
		}
	}

	/** The number in the spot's code: 12 for A12. */
	public int getNumber() {
		return Integer.parseInt(code.substring(zone.length()));
	}

	public void reserve(String driverId, UUID reservationId) {
		state.reserve(this);
		hold(driverId, reservationId);
	}

	/** A driver may park on a free spot, or on a spot they reserved themselves. The session becomes the hold. */
	public void occupy(String driverId, UUID sessionId) {
		if (status == SpotStatus.RESERVED && !driverId.equals(heldBy)) {
			throw new IllegalSpotStateTransitionException("Spot " + code + " is reserved by another driver.");
		}
		state.occupy(this);
		hold(driverId, sessionId);
	}

	/**
	 * Ends a hold. Only the current hold can be ended, so a late "reservation expired" can't free
	 * the spot once the driver has parked (the session is the hold now) or reserved it again.
	 */
	public void release(UUID endedHoldId) {
		if (!isHeldBy(endedHoldId)) {
			throw new IllegalSpotStateTransitionException("Spot " + code + " is no longer held by that reservation or session.");
		}
		state.release(this);
	}

	public boolean isHeldBy(UUID candidateHoldId) {
		return holdId != null && holdId.equals(candidateHoldId);
	}

	public void markOutOfService() {
		state.markOutOfService(this);
	}

	public void returnToService() {
		state.returnToService(this);
	}

	/** Called only by the state objects; keeps the stored status in step with the state. */
	void changeState(SpotState newState) {
		this.state = newState;
		this.status = newState.getStatus();
		if (status == SpotStatus.AVAILABLE || status == SpotStatus.OUT_OF_SERVICE) {
			hold(null, null);
		}
	}

	private void hold(String driverId, UUID newHoldId) {
		this.heldBy = driverId;
		this.holdId = newHoldId;
	}

	@PostLoad
	void restoreState() {
		this.state = switch (status) {
			case AVAILABLE -> new AvailableState();
			case RESERVED -> new ReservedState();
			case OCCUPIED -> new OccupiedState();
			case OUT_OF_SERVICE -> new OutOfServiceState();
		};
	}

}
