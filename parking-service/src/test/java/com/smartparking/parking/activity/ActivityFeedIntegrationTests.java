package com.smartparking.parking.activity;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.parking.TestcontainersConfiguration;
import com.smartparking.parking.activity.dto.ActivityItem;
import com.smartparking.parking.activity.service.ActivityFeed;
import com.smartparking.parking.messaging.MessagingConfig;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Events from the other services, over a real RabbitMQ, end up in the admin's feed and on its STOMP topic. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ActivityFeedIntegrationTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private MockMvc mvc;

	@MockitoSpyBean
	private SimpMessagingTemplate messagingTemplate;

	@Test
	void reservationSessionAndPaymentEventsReachTheFeed() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		publish("reservation.created", Map.of("eventId", UUID.randomUUID(), "occurredAt", "2026-03-04T08:00:00Z",
				"driverId", driver, "spotCode", "A1", "locationName", "Central Garage", "expiresAt", "2026-03-04T08:15:00Z"));
		publish("session.completed", Map.of("eventId", UUID.randomUUID(), "occurredAt", "2026-03-04T09:00:00Z",
				"driverId", driver, "spotCode", "A1", "locationName", "Central Garage", "cost", 12));
		publish("payment.failed", Map.of("eventId", UUID.randomUUID(), "occurredAt", "2026-03-04T09:00:01Z",
				"driverId", driver, "amount", 12, "method", "CARD", "attempts", 1));

		verify(messagingTemplate, timeout(5000)).convertAndSend(eq(ActivityFeed.TOPIC),
				argThat((Object item) -> item instanceof ActivityItem a && a.type().equals("payment.failed") && driver.equals(a.driverId())));
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> mvc.perform(get("/api/v1/activity"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.driverId == '%s')].type".formatted(driver))
				.value(org.hamcrest.Matchers.contains("payment.failed", "session.completed", "reservation.created")))
			.andExpect(jsonPath("$[?(@.driverId == '%s' && @.type == 'session.completed')].amount".formatted(driver))
				.value(org.hamcrest.Matchers.contains(12))));
	}

	private void publish(String routingKey, Map<String, Object> event) {
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, routingKey, event);
	}

}
