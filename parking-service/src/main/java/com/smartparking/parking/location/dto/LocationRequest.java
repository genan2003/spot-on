package com.smartparking.parking.location.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Creates or edits a location. Pricing is set separately (a new location starts at 5 lei per hour). Opening hours
 * are optional: leave both out for a car park that is always open; {@code closesAt} before {@code opensAt} means it
 * closes after midnight.
 */
public record LocationRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 200) String address,
		@DecimalMin("-90") @DecimalMax("90") double latitude,
		@DecimalMin("-180") @DecimalMax("180") double longitude,
		@JsonFormat(pattern = "HH:mm") LocalTime opensAt,
		@JsonFormat(pattern = "HH:mm") LocalTime closesAt) {

	@AssertTrue(message = "Give both an opening and a closing time, and make them different, or leave both out for a car park that is always open.")
	public boolean isOpeningHoursValid() {
		return opensAt == null ? closesAt == null : closesAt != null && !opensAt.equals(closesAt);
	}

}
