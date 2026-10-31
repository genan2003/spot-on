package com.smartparking.payment.payment.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartparking.payment.account.domain.PaymentAccount;
import com.smartparking.payment.account.service.PaymentAccountService;
import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Factory returns one product per method; card fails at the configured rate, the wallet when money is short. */
class PaymentProcessorFactoryTests {

	private final Payment payment = new Payment(UUID.randomUUID(), "driver-1", new BigDecimal("18.00"), PaymentMethod.CARD, Instant.now());
	private final PaymentAccountService accounts = mock(PaymentAccountService.class);

	@Test
	void createsTheProcessorForEachMethod() {
		PaymentProcessorFactory factory = new PaymentProcessorFactory(0.0, new Random(1), accounts);
		assertThat(factory.create(PaymentMethod.CARD)).isInstanceOf(CardPaymentProcessor.class);
		assertThat(factory.create(PaymentMethod.WALLET)).isInstanceOf(WalletPaymentProcessor.class);
	}

	@Test
	void theCardFollowsTheFailureRate() {
		assertThat(new PaymentProcessorFactory(0.0, new Random(1), accounts).create(PaymentMethod.CARD).process(payment).success()).isTrue();
		assertThat(new PaymentProcessorFactory(1.0, new Random(1), accounts).create(PaymentMethod.CARD).process(payment).success()).isFalse();
	}

	@Test
	void theWalletPaysWhenTheBalanceIsEnoughAndDebitsIt() {
		PaymentAccount account = new PaymentAccount("driver-1", PaymentMethod.WALLET, new BigDecimal("25.00"));
		when(accounts.lockForPayment("driver-1")).thenReturn(account);
		// A failure rate of 1 would fail any card payment; the wallet ignores it.
		PaymentResult result = new PaymentProcessorFactory(1.0, new Random(1), accounts).create(PaymentMethod.WALLET).process(payment);

		assertThat(result.success()).isTrue();
		assertThat(account.getWalletBalance()).isEqualByComparingTo("7.00");
	}

	@Test
	void theWalletRefusesWhenTheBalanceIsTooLow() {
		PaymentAccount account = new PaymentAccount("driver-1", PaymentMethod.WALLET, new BigDecimal("12.00"));
		when(accounts.lockForPayment("driver-1")).thenReturn(account);

		PaymentResult result = new PaymentProcessorFactory(0.0, new Random(1), accounts).create(PaymentMethod.WALLET).process(payment);

		assertThat(result.success()).isFalse();
		assertThat(result.message()).isEqualTo("Not enough money in the wallet: 12.00 lei left, 18.00 lei due.");
		assertThat(account.getWalletBalance()).isEqualByComparingTo("12.00");
	}

}
