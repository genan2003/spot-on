package com.smartparking.parking.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates or edits a location. Pricing is set separately (a new location starts at 5 lei per hour). */
public record LocationRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 200) String address,
		@DecimalMin("-90") @DecimalMax("90") double latitude,
		@DecimalMin("-180") @DecimalMax("180") double longitude) {
}
