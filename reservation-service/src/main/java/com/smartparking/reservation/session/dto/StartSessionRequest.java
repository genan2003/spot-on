package com.smartparking.reservation.session.dto;

import com.smartparking.reservation.session.domain.LicencePlates;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * {@code reservationId} is optional: a driver can also drive in without reserving. {@code licencePlate} is the car's
 * plate, shown to the admin on the occupied bay.
 */
public record StartSessionRequest(@NotNull UUID spotId, UUID reservationId, @NotBlank String driverId,
		@NotBlank(message = "Enter the car's licence plate.")
		@Pattern(regexp = LicencePlates.PATTERN, message = "Enter the plate as it is on the car, e.g. CJ 07 ABC: 2 to 12 letters, digits, spaces or hyphens.")
		String licencePlate) {
}
