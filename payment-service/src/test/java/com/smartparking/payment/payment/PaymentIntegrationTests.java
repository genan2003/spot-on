package com.smartparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.payment.TestcontainersConfiguration;
import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import com.smartparking.payment.payment.domain.PaymentStatus;
import com.smartparking.payment.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Charging, retrying and listing payments against real PostgreSQL. The random generator is mocked
 * so a payment fails or succeeds on purpose (failure rate 0.2: a draw below 0.2 fails).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "smartparking.payment.failure-rate=0.2")
@AutoConfigureMockMvc
class PaymentIntegrationTests {

	private static final double FAILS = 0.1;
	private static final double SUCCEEDS = 0.9;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RandomGenerator random;

	@Test
	void aFailedPaymentCanBeRetriedUntilItGoesThrough() throws Exception {
		when(random.nextDouble()).thenReturn(FAILS, SUCCEEDS);
		String driver = "driver-" + UUID.randomUUID();
		UUID session = UUID.randomUUID();

		Payment payment = paymentService.charge(session, driver, new BigDecimal("18.00"), PaymentMethod.CARD);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

		mvc.perform(post("/api/v1/payments/{id}/retry", payment.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.attempts").value(2));
		mvc.perform(post("/api/v1/payments/{id}/retry", payment.getId())).andExpect(status().isConflict());

		mvc.perform(get("/api/v1/payments").param("driverId", driver))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].amount").value(18.0));
		mvc.perform(get("/api/v1/payments").param("sessionId", session.toString()))
			.andExpect(jsonPath("$.id").value(payment.getId().toString()));
	}

	@Test
	void aSessionIsChargedOnlyOnce() {
		when(random.nextDouble()).thenReturn(SUCCEEDS);
		UUID session = UUID.randomUUID();

		Payment first = paymentService.charge(session, "driver-1", new BigDecimal("6.00"), PaymentMethod.WALLET);
		Payment again = paymentService.charge(session, "driver-1", new BigDecimal("6.00"), PaymentMethod.WALLET);

		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(again.getAttempts()).isEqualTo(1);
	}

	@Test
	void aFreeStayIsPaidWithoutAskingTheProcessor() {
		// Would fail if the processor were asked.
		when(random.nextDouble()).thenReturn(FAILS);

		Payment payment = paymentService.charge(UUID.randomUUID(), "driver-1", new BigDecimal("0.00"), PaymentMethod.WALLET);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
	}

	@Test
	void aDriverWhoChoseTheWalletPaysFromIt() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		mvc.perform(get("/api/v1/payment-accounts/{driverId}", driver))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.preferredMethod").value("CARD"))
			.andExpect(jsonPath("$.walletBalance").value(25.0));
		mvc.perform(patch("/api/v1/payment-accounts/{driverId}", driver).contentType(MediaType.APPLICATION_JSON)
				.content("{\"preferredMethod\":\"WALLET\"}"))
			.andExpect(jsonPath("$.preferredMethod").value("WALLET"));

		Payment paid = paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("15.00"));
		assertThat(paid.getMethod()).isEqualTo(PaymentMethod.WALLET);
		assertThat(paid.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
		mvc.perform(get("/api/v1/payment-accounts/{driverId}", driver)).andExpect(jsonPath("$.walletBalance").value(10.0));
	}

	@Test
	void aWalletPaymentThatDoesNotFitCanBePaidByCardOrAfterATopUp() throws Exception {
		when(random.nextDouble()).thenReturn(SUCCEEDS);
		String driver = "driver-" + UUID.randomUUID();
		mvc.perform(patch("/api/v1/payment-accounts/{driverId}", driver).contentType(MediaType.APPLICATION_JSON)
			.content("{\"preferredMethod\":\"WALLET\"}"));

		Payment tooBig = paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("30.00"));
		assertThat(tooBig.getStatus()).isEqualTo(PaymentStatus.FAILED);
		mvc.perform(post("/api/v1/payments/{id}/retry", tooBig.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"CARD\"}"))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.method").value("CARD"));

		Payment second = paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("40.00"));
		assertThat(second.getStatus()).isEqualTo(PaymentStatus.FAILED);
		mvc.perform(post("/api/v1/payment-accounts/{driverId}/top-ups", driver).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":50}"))
			.andExpect(jsonPath("$.walletBalance").value(75.0));
		mvc.perform(post("/api/v1/payments/{id}/retry", second.getId()))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.method").value("WALLET"));
		mvc.perform(get("/api/v1/payment-accounts/{driverId}", driver)).andExpect(jsonPath("$.walletBalance").value(35.0));
	}

	/** The wallet row is locked while a payment uses it, so two payments can't spend the same money. */
	@Test
	void twoPaymentsAtOnceCannotSpendTheSameMoney() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("0.00")); // creates the account (25 lei)
		mvc.perform(patch("/api/v1/payment-accounts/{driverId}", driver).contentType(MediaType.APPLICATION_JSON)
			.content("{\"preferredMethod\":\"WALLET\"}"));

		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			List<Future<Payment>> payments = List.of(
					pool.submit(() -> { start.await(); return paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("15.00")); }),
					pool.submit(() -> { start.await(); return paymentService.charge(UUID.randomUUID(), driver, new BigDecimal("15.00")); }));
			start.countDown();
			List<PaymentStatus> outcomes = new java.util.ArrayList<>();
			for (Future<Payment> payment : payments) {
				outcomes.add(payment.get().getStatus());
			}
			assertThat(outcomes).containsExactlyInAnyOrder(PaymentStatus.COMPLETED, PaymentStatus.FAILED);
		}
		mvc.perform(get("/api/v1/payment-accounts/{driverId}", driver)).andExpect(jsonPath("$.walletBalance").value(10.0));
	}

	@Test
	void topUpsMustBeBetween1And500Lei() throws Exception {
		for (String amount : new String[] {"0", "500.01", "-5"}) {
			mvc.perform(post("/api/v1/payment-accounts/{driverId}/top-ups", "driver-1").contentType(MediaType.APPLICATION_JSON)
					.content("{\"amount\":" + amount + "}"))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void unknownPaymentIs404() throws Exception {
		mvc.perform(post("/api/v1/payments/{id}/retry", UUID.randomUUID())).andExpect(status().isNotFound());
	}

}
