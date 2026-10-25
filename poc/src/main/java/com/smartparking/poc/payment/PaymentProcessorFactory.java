package com.smartparking.poc.payment;

import java.util.Map;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <b>Factory pattern: Factory</b> (simple factory). Returns the processor for a payment method,
 * so callers depend only on {@link PaymentProcessor}. Adding a method means changing this class
 * only, never its callers.
 */
@Component
public class PaymentProcessorFactory {

    private final Map<PaymentMethod, PaymentProcessor> processors;

    public PaymentProcessorFactory(@Value("${smartparking.payment.failure-rate}") double failureRate, RandomGenerator random) {
        this.processors = Map.of(
                PaymentMethod.CARD, new CardPaymentProcessor(failureRate, random),
                PaymentMethod.WALLET, new WalletPaymentProcessor(failureRate, random));
    }

    public PaymentProcessor create(PaymentMethod method) {
        return processors.get(method);
    }
}
