package com.smartparking.payment.payment.dto;

import com.smartparking.payment.payment.domain.PaymentMethod;

/** Optional body of a retry: {@code method} switches a failed payment to the other method first. */
public record RetryPaymentRequest(PaymentMethod method) {
}
