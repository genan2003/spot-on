package com.smartparking.payment.payment.processor;

import com.smartparking.payment.account.domain.PaymentAccount;
import com.smartparking.payment.account.service.PaymentAccountService;
import com.smartparking.payment.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <b>Factory pattern: Concrete product.</b> Pays from the driver's simulated wallet. The account row is locked
 * for the payment's transaction, so two payments can't spend the same money. Fails only when the balance is
 * too low (unlike the card, it has no random failures).
 */
@Slf4j
@RequiredArgsConstructor
public class WalletPaymentProcessor implements PaymentProcessor {

	private final PaymentAccountService accounts;

	@Override
	public PaymentResult process(Payment payment) {
		PaymentAccount account = accounts.lockForPayment(payment.getDriverId());
		if (!account.canPay(payment.getAmount())) {
			log.info("[WALLET] {} lei due, only {} lei in the wallet", payment.getAmount(), account.getWalletBalance());
			return new PaymentResult(false, "Not enough money in the wallet: %s lei left, %s lei due."
				.formatted(account.getWalletBalance(), payment.getAmount()));
		}
		account.debit(payment.getAmount());
		log.info("[WALLET] Debited {} lei, {} lei left", payment.getAmount(), account.getWalletBalance());
		return new PaymentResult(true, "Paid from the wallet.");
	}

}
