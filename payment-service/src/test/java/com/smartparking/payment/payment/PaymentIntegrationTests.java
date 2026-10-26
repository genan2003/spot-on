package com.smartparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.payment.TestcontainersConfiguration;
import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import com.smartparking.payment.payment.domain.PaymentStatus;
import com.smartparking.payment.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
	void unknownPaymentIs404() throws Exception {
		mvc.perform(post("/api/v1/payments/{id}/retry", UUID.randomUUID())).andExpect(status().isNotFound());
	}

}
