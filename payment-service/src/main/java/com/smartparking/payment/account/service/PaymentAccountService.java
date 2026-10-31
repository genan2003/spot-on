package com.smartparking.payment.account.service;

import com.smartparking.payment.account.domain.PaymentAccount;
import com.smartparking.payment.account.repository.PaymentAccountRepository;
import com.smartparking.payment.payment.domain.PaymentMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drivers' payment settings and wallets. There are no driver accounts elsewhere, so an account is created the
 * first time a driver is seen: card payments, and a wallet with {@code starting-balance} lei to try it with.
 */
@Service
public class PaymentAccountService {

	private final PaymentAccountRepository accountRepository;
	private final EntityManager entityManager;
	private final BigDecimal startingBalance;

	public PaymentAccountService(PaymentAccountRepository accountRepository, EntityManager entityManager,
			@Value("${smartparking.payment.wallet-starting-balance}") BigDecimal startingBalance) {
		this.accountRepository = accountRepository;
		this.entityManager = entityManager;
		this.startingBalance = startingBalance;
	}

	@Transactional
	public PaymentAccount get(String driverId) {
		return accountRepository.findById(driverId).orElseGet(() -> create(driverId));
	}

	@Transactional
	public PaymentAccount choose(String driverId, PaymentMethod method) {
		PaymentAccount account = get(driverId);
		account.choose(method);
		return account;
	}

	/** Simulated: no money moves anywhere, the balance just goes up. */
	@Transactional
	public PaymentAccount topUp(String driverId, BigDecimal amount) {
		PaymentAccount account = get(driverId);
		account.topUp(amount);
		return account;
	}

	/**
	 * The account, locked ({@code SELECT … FOR UPDATE}) for the rest of the payment's transaction, used by
	 * {@code WalletPaymentProcessor}. A refresh rather than a query: the payment has usually read the account
	 * already (for its method), and a second payment may have changed it since; refreshing under the lock loads
	 * the balance that payment left, instead of failing on the older copy.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public PaymentAccount lockForPayment(String driverId) {
		PaymentAccount account = get(driverId);
		entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
		return account;
	}

	private PaymentAccount create(String driverId) {
		return accountRepository.saveAndFlush(new PaymentAccount(driverId, PaymentMethod.CARD, startingBalance));
	}

}
