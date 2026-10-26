package com.smartparking.parking.location.domain;

/** Which pricing strategy a location uses. The strategies themselves live in reservation-service, where cost is calculated. */
public enum PricingStrategyType {
	HOURLY,
	FLAT_RATE,
	TIERED,
	NIGHT_WEEKEND
}
