package com.smartparking.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smartparking.notification.TestcontainersConfiguration;
import com.smartparking.notification.notification.domain.Notification;
import com.smartparking.notification.notification.domain.NotificationKind;
import com.smartparking.notification.notification.repository.NotificationRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Events in over a real RabbitMQ, notifications out: one per event, none for a duplicate. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DriverEventListenerTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationRepository notificationRepository;

	@Test
	void everyEventBecomesOneNotification() {
		String driver = "driver-" + UUID.randomUUID();
		UUID created = UUID.randomUUID();
		publish("reservation.created", Map.of("eventId", created, "driverId", driver, "spotCode", "A1",
				"locationName", "Central Garage", "expiresAt", "2026-03-04T08:15:00Z"));
		publish("reservation.created", Map.of("eventId", created, "driverId", driver, "spotCode", "A1",
				"locationName", "Central Garage", "expiresAt", "2026-03-04T08:15:00Z")); // redelivered
		publish("session.completed", Map.of("eventId", UUID.randomUUID(), "driverId", driver, "spotCode", "A1",
				"locationName", "Central Garage", "cost", 16));
		publish("payment.failed", Map.of("eventId", UUID.randomUUID(), "driverId", driver, "amount", 16));

		await().atMost(Duration.ofSeconds(10)).until(() -> notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver).size() >= 3);
		List<Notification> sent = notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver);

		assertThat(sent).hasSize(3);
		assertThat(sent).extracting(Notification::getKind).containsExactlyInAnyOrder(
				NotificationKind.RESERVATION_CREATED, NotificationKind.SESSION_COMPLETED, NotificationKind.PAYMENT_FAILED);
		assertThat(sent).extracting(Notification::getMessage).contains(
				"A1 at Central Garage is held for you until 10:15.",
				"Your session at A1, Central Garage ended. It cost 16.00 lei.",
				"Payment of 16.00 lei failed. Try payment again from your receipt.");
	}

	@Test
	void aMovedAndAnExtendedReservationAreTold() {
		String driver = "driver-" + UUID.randomUUID();
		publish("reservation.changed", Map.of("eventId", UUID.randomUUID(), "driverId", driver, "spotCode", "B2",
				"previousSpotCode", "A1", "locationName", "Central Garage", "expiresAt", "2026-03-04T08:15:00Z"));
		publish("reservation.changed", Map.of("eventId", UUID.randomUUID(), "driverId", driver, "spotCode", "B2",
				"locationName", "Central Garage", "expiresAt", "2026-03-04T08:30:00Z"));

		await().atMost(Duration.ofSeconds(10)).until(() -> notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver).size() >= 2);
		assertThat(notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver))
			.allMatch(n -> n.getKind() == NotificationKind.RESERVATION_CHANGED)
			.extracting(Notification::getMessage)
			.containsExactlyInAnyOrder("Your reservation moved from A1 to B2. It is held until 10:15.",
					"B2 is now held for you until 10:30.");
	}

	@Test
	void aReservationCancelledBecauseTheSpotClosedSaysSo() {
		String driver = "driver-" + UUID.randomUUID();
		publish("reservation.cancelled", Map.of("eventId", UUID.randomUUID(), "driverId", driver, "spotCode", "A3",
				"locationName", "Central Garage", "reason", "SPOT_CLOSED"));

		await().atMost(Duration.ofSeconds(10)).until(() -> !notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver).isEmpty());
		assertThat(notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driver)).extracting(Notification::getMessage)
			.containsExactly("A3 at Central Garage was closed by the car park, so your reservation was cancelled. Pick another free spot.");
	}

	private void publish(String routingKey, Map<String, Object> event) {
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, routingKey, event);
	}

}
