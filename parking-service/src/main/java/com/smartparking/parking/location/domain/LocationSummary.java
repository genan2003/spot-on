package com.smartparking.parking.location.domain;

/** A location with its spot counts, as drivers and admins see it in lists. */
public record LocationSummary(ParkingLocation location, long totalSpots, long freeSpots) {
}
