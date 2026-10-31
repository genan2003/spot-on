package com.smartparking.payment.account.dto;

import com.smartparking.payment.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** The method the driver's next payments use. */
public record UpdatePaymentAccountRequest(@NotNull PaymentMethod preferredMethod) {
}
