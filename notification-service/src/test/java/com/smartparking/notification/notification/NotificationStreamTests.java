package com.smartparking.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smartparking.notification.TestcontainersConfiguration;
import com.smartparking.notification.messaging.MessagingConfig;
import com.smartparking.notification.notification.live.NotificationStreams;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Live notifications end to end inside the service: an event arrives over RabbitMQ, the notification is saved,
 * notification.sent goes round through RabbitMQ again and reaches the driver's open Server-Sent Events stream.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationStreamTests {

	@LocalServerPort
	private int port;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationStreams streams;

	@Test
	void aNewNotificationReachesTheDriversOpenStreamOnly() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		String otherDriver = "driver-" + UUID.randomUUID();
		List<String> received = listen(driver);
		List<String> receivedByOther = listen(otherDriver);
		await().atMost(Duration.ofSeconds(5)).until(() -> streams.openStreams() >= 2);

		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "reservation.created", Map.of("eventId", UUID.randomUUID(),
				"driverId", driver, "spotCode", "A1", "locationName", "Central Garage", "expiresAt", "2026-03-04T08:15:00Z"));

		await().atMost(Duration.ofSeconds(10)).until(() -> received.stream().anyMatch(line -> line.startsWith("data:")));
		assertThat(received).contains("event:notification");
		assertThat(received).anyMatch(line -> line.startsWith("data:") && line.contains("\"kind\":\"RESERVATION_CREATED\"")
				&& line.contains("A1 at Central Garage is held for you until 10:15."));
		assertThat(receivedByOther).noneMatch(line -> line.startsWith("data:"));
	}

	/** Opens the driver's stream and collects its lines in the background. */
	private List<String> listen(String driverId) {
		List<String> lines = new CopyOnWriteArrayList<>();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/notifications/stream?driverId=" + driverId))
			.header("Accept", "text/event-stream")
			.build();
		HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenAccept(response -> {
			try (InputStream body = response.body();
					BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
			catch (Exception ignored) {
				// The test ended and closed the stream.
			}
		});
		return lines;
	}

}
