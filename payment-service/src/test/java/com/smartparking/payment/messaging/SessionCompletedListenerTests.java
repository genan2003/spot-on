package com.smartparking.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.smartparking.payment.PublishedEvents;
import com.smartparking.payment.TestcontainersConfiguration;
import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentStatus;
import com.smartparking.payment.payment.repository.PaymentRepository;
import com.smartparking.payment.payment.service.PaymentService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** session.completed in, payment.failed / payment.completed out, over a real RabbitMQ. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "smartparking.payment.failure-rate=0.2")
class SessionCompletedListenerTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentService paymentService;

	@MockitoBean
	private RandomGenerator random;

	@Test
	void aCompletedSessionIsChargedOnceAndTheOutcomeIsPublished() {
		PublishedEvents events = new PublishedEvents(rabbitTemplate);
		when(random.nextDouble()).thenReturn(0.1, 0.9); // first attempt fails, the retry succeeds
		UUID session = UUID.randomUUID();
		Map<String, Object> event = Map.of("eventId", UUID.randomUUID(), "sessionId", session, "driverId", "driver-1",
				"spotCode", "A1", "cost", 16.00);

		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "session.completed", event);
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "session.completed", event); // redelivered

		await().atMost(Duration.ofSeconds(10)).until(() -> paymentRepository.findBySessionId(session).isPresent());
		String failed = events.await("payment.failed", session.toString());
		Payment payment = paymentRepository.findBySessionId(session).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(payment.getAmount()).isEqualByComparingTo("16.00");
		assertThat(PublishedEvents.<String>read(failed, "$.paymentId")).isEqualTo(payment.getId().toString());

		paymentService.retry(payment.getId());
		events.await("payment.completed", payment.getId().toString());
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getAttempts()).isEqualTo(2);
	}

}
