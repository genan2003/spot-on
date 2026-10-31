package com.smartparking.parking.spot.domain;

/**
 * <b>State pattern: Concrete state.</b> A reserved spot can be occupied (the driver arrives),
 * released (cancelled or expired), or taken out of service by an admin: there is no car in it yet,
 * so the reservation is cancelled instead. Reserving it again is rejected: this is the in-code half of
 * the "no double reservations" rule; the database guards the concurrent case.
 */
public class ReservedState implements SpotState {

	@Override
	public SpotStatus getStatus() {
		return SpotStatus.RESERVED;
	}

	@Override
	public void occupy(ParkingSpot spot) {
		spot.changeState(new OccupiedState());
	}

	@Override
	public void release(ParkingSpot spot) {
		spot.changeState(new AvailableState());
	}

	@Override
	public void markOutOfService(ParkingSpot spot) {
		spot.changeState(new OutOfServiceState());
	}
}
