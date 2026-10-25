package com.smartparking.poc.spot;

/** <b>State pattern: Concrete state.</b> A free spot can be reserved, occupied or closed. */
public class AvailableState implements SpotState {

    @Override
    public SpotStatus getStatus() {
        return SpotStatus.AVAILABLE;
    }

    @Override
    public void reserve(ParkingSpot spot) {
        spot.changeState(new ReservedState());
    }

    @Override
    public void occupy(ParkingSpot spot) {
        spot.changeState(new OccupiedState());
    }

    @Override
    public void markOutOfService(ParkingSpot spot) {
        spot.changeState(new OutOfServiceState());
    }
}
