package com.smartparking.payment.account.domain;

import com.smartparking.payment.payment.domain.PaymentMethod;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A driver's payment settings: the method new payments use (the Factory picks the processor from it) and a
 * simulated wallet. Money never goes below zero: a wallet payment that doesn't fit fails instead.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAccount {

	@Id
	private String driverId;

	@Enumerated(EnumType.STRING)
	private PaymentMethod preferredMethod;

	private BigDecimal walletBalance;

	@Version
	private long version;

	public PaymentAccount(String driverId, PaymentMethod preferredMethod, BigDecimal walletBalance) {
		this.driverId = driverId;
		this.preferredMethod = preferredMethod;
		this.walletBalance = walletBalance;
	}

	public void choose(PaymentMethod method) {
		this.preferredMethod = method;
	}

	public void topUp(BigDecimal amount) {
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("A top-up must be more than 0 lei.");
		}
		walletBalance = walletBalance.add(amount);
	}

	public boolean canPay(BigDecimal amount) {
		return walletBalance.compareTo(amount) >= 0;
	}

	public void debit(BigDecimal amount) {
		if (!canPay(amount)) {
			throw new IllegalStateException("Not enough money in the wallet.");
		}
		walletBalance = walletBalance.subtract(amount);
	}

}
