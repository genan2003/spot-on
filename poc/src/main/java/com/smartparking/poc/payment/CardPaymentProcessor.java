package com.smartparking.poc.payment;

import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <b>Factory pattern: Concrete product.</b> Simulated card payment. Fails with probability
 * {@code failureRate} so the "payment failed" path can be shown.
 */
@Slf4j
@RequiredArgsConstructor
public class CardPaymentProcessor implements PaymentProcessor {

    private final double failureRate;
    private final RandomGenerator random;

    @Override
    public PaymentResult process(Payment payment) {
        if (random.nextDouble() < failureRate) {
            log.info("[CARD] Declined {} lei", payment.getAmount());
            return new PaymentResult(false, "Card declined by the bank.");
        }
        log.info("[CARD] Charged {} lei", payment.getAmount());
        return new PaymentResult(true, "Card charged.");
    }
}
