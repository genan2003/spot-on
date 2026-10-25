package com.smartparking.poc.spot;

/**
 * <b>State pattern: Concrete state.</b> A reserved spot can be occupied (the driver arrives) or
 * released (cancelled or expired). Reserving it again is rejected: this is the in-code half of
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
}
