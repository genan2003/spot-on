package com.smartparking.poc.spot;

/** <b>State pattern: Concrete state.</b> An occupied spot can only be released when the car leaves. */
public class OccupiedState implements SpotState {

    @Override
    public SpotStatus getStatus() {
        return SpotStatus.OCCUPIED;
    }

    @Override
    public void release(ParkingSpot spot) {
        spot.changeState(new AvailableState());
    }
}
