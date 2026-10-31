package com.smartparking.parking.spot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import jakarta.validation.constraints.AssertTrue;

/**
 * Admin change to one spot; every field is optional and a missing one stays as it is.
 * {@code status}: {@code OUT_OF_SERVICE} closes a free or reserved spot, {@code AVAILABLE} reopens a closed one.
 * {@code type}, {@code evCharging}, {@code accessible}: the spot's properties.
 */
public record SpotStatusRequest(SpotStatus status, SpotType type, Boolean evCharging, Boolean accessible) {

	public boolean changesProperties() {
		return type != null || evCharging != null || accessible != null;
	}

	@JsonIgnore
	@AssertTrue(message = "must change the status or at least one property")
	public boolean isChangingSomething() {
		return status != null || changesProperties();
	}

}
