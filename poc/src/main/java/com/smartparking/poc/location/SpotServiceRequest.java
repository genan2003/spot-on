package com.smartparking.poc.location;

/** {@code true} takes the spot out of service, {@code false} returns it to service. */
public record SpotServiceRequest(boolean outOfService) {
}
