package com.smartparking.payment.payment.processor;

import com.smartparking.payment.payment.domain.Payment;

/** <b>Factory pattern: Product.</b> Charges a payment; callers never see the concrete class. */
public interface PaymentProcessor {

	PaymentResult process(Payment payment);

}
