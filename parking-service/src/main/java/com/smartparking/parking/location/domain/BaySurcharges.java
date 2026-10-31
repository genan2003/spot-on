package com.smartparking.parking.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Percentages added per bay type (negative for a discount), plus one for EV charging. 0 = no surcharge. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BaySurcharges {

	public static BaySurcharges none() {
		return new BaySurcharges(0, 0, 0, 0);
	}

	@Column(name = "compact_surcharge")
	private int compact;

	@Column(name = "large_surcharge")
	private int large;

	@Column(name = "motorcycle_surcharge")
	private int motorcycle;

	@Column(name = "ev_charging_surcharge")
	private int evCharging;

}
