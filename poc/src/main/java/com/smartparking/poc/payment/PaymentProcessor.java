package com.smartparking.poc.payment;

/** <b>Factory pattern: Product.</b> Pays for a session; callers never see the concrete class. */
public interface PaymentProcessor {

    PaymentResult process(Payment payment);
}
