package com.smartparking.parking.spot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartparking.parking.spot.domain.SpotType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Sets the same properties on several spots of one location; a missing property stays as it is on each spot. */
public record BulkSpotUpdateRequest(
		@NotEmpty @Size(max = 200) Set<UUID> spotIds,
		SpotType type,
		Boolean evCharging,
		Boolean accessible) {

	@JsonIgnore
	@AssertTrue(message = "must change at least one property")
	public boolean isChangingSomething() {
		return type != null || evCharging != null || accessible != null;
	}

}
