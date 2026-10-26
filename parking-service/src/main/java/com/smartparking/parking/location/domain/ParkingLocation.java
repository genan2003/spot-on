package com.smartparking.parking.location.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A car park. <b>Strategy pattern: Context.</b> It holds the {@link PricingRule} the admin chose;
 * changing the rule changes how every later session here is charged, without touching the code
 * that ends sessions.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingLocation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	private String address;

	private double latitude;

	private double longitude;

	@Embedded
	private PricingRule pricingRule;

	public ParkingLocation(String name, String address, double latitude, double longitude, PricingRule pricingRule) {
		this.name = name;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.pricingRule = pricingRule;
	}

	public void update(String name, String address, double latitude, double longitude) {
		this.name = name;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public void changePricingRule(PricingRule pricingRule) {
		this.pricingRule = pricingRule;
	}

}
