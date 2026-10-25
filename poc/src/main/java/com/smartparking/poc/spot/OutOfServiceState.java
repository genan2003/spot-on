package com.smartparking.poc.spot;

/** <b>State pattern: Concrete state.</b> A closed spot can only be returned to service. */
public class OutOfServiceState implements SpotState {

    @Override
    public SpotStatus getStatus() {
        return SpotStatus.OUT_OF_SERVICE;
    }

    @Override
    public void returnToService(ParkingSpot spot) {
        spot.changeState(new AvailableState());
    }
}
