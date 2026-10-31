package com.smartparking.payment.account.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Simulated top-up: between 1 and 500 lei, whole bani. */
public record TopUpRequest(
		@NotNull @DecimalMin(value = "1", message = "Add at least 1 leu.")
		@DecimalMax(value = "500", message = "Add at most 500 lei at a time.")
		@Digits(integer = 3, fraction = 2) BigDecimal amount) {
}
