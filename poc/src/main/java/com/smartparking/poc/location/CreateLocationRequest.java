package com.smartparking.poc.location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLocationRequest(
        @NotBlank String name,
        @NotBlank String address,
        @DecimalMin("-90") @DecimalMax("90") double latitude,
        @DecimalMin("-180") @DecimalMax("180") double longitude,
        @NotNull @Valid PricingRuleDto pricingRule) {
}
