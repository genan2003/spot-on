package com.smartparking.payment.account.dto;

import com.smartparking.payment.payment.domain.PaymentMethod;
import java.math.BigDecimal;

public record PaymentAccountResponse(String driverId, PaymentMethod preferredMethod, BigDecimal walletBalance) {
}
