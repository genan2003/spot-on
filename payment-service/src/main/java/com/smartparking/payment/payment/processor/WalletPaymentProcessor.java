package com.smartparking.payment.payment.processor;

import com.smartparking.payment.payment.domain.Payment;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <b>Factory pattern: Concrete product.</b> Simulated in-app wallet payment. Fails with probability {@code failureRate}, as if the wallet balance were too low.
 */
@Slf4j
@RequiredArgsConstructor
public class WalletPaymentProcessor implements PaymentProcessor {

	private final double failureRate;
	private final RandomGenerator random;

	@Override
	public PaymentResult process(Payment payment) {
		if (random.nextDouble() < failureRate) {
			log.info("[WALLET] Not enough balance for {} lei", payment.getAmount());
			return new PaymentResult(false, "Not enough money in the wallet.");
		}
		log.info("[WALLET] Debited {} lei", payment.getAmount());
		return new PaymentResult(true, "Wallet debited.");
	}

}
