package com.smartparking.parking.location.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalTime;
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

	/** Local time it opens; {@code null} with {@link #closesAt}: open around the clock. */
	private LocalTime opensAt;

	/** Local time it closes; earlier than {@link #opensAt} when it closes after midnight. */
	private LocalTime closesAt;

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

	/** Both {@code null}: open around the clock. Otherwise both set and different. */
	public void changeOpeningHours(LocalTime opensAt, LocalTime closesAt) {
		if ((opensAt == null) != (closesAt == null) || (opensAt != null && opensAt.equals(closesAt))) {
			throw new IllegalArgumentException("Give both an opening and a closing time, and make them different.");
		}
		this.opensAt = opensAt;
		this.closesAt = closesAt;
	}

	public boolean isOpenAllDay() {
		return opensAt == null;
	}

	/** Whether drivers may reserve or start parking at this local time. Closing time itself is closed. */
	public boolean isOpenAt(LocalTime time) {
		if (isOpenAllDay()) {
			return true;
		}
		if (opensAt.isBefore(closesAt)) {
			return !time.isBefore(opensAt) && time.isBefore(closesAt);
		}
		// Open past midnight, e.g. 22:00 to 06:00.
		return !time.isBefore(opensAt) || time.isBefore(closesAt);
	}

	public void changePricingRule(PricingRule pricingRule) {
		this.pricingRule = pricingRule;
	}

}
