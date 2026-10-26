package com.smartparking.payment.payment.processor;

/** Outcome of a simulated payment. */
public record PaymentResult(boolean success, String message) {
}
