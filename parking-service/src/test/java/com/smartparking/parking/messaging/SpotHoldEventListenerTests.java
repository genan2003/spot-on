package com.smartparking.parking.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smartparking.parking.TestcontainersConfiguration;
import com.smartparking.parking.location.service.ParkingLocationService;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import com.smartparking.parking.spot.service.SpotHoldService;
import com.smartparking.parking.spot.service.SpotService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Release events arriving over a real RabbitMQ: the right event frees the spot; a duplicate or a
 * stale event (for a hold that already ended) changes nothing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpotHoldEventListenerTests {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ParkingLocationService locationService;

	@Autowired
	private SpotService spotService;

	@Autowired
	private SpotHoldService holdService;

	@Autowired
	private ParkingSpotRepository spotRepository;

	@Autowired
	private ProcessedEventRepository processedEventRepository;

	@Test
	void expiredReservationFreesTheSpotOnlyOnce() {
		UUID spot = newSpot();
		UUID firstReservation = UUID.randomUUID();
		holdService.reserve(spot, "driver-1", firstReservation);

		UUID eventId = UUID.randomUUID();
		publish("reservation.expired", eventId, spot, firstReservation, null);
		awaitStatus(spot, SpotStatus.AVAILABLE);

		// The driver reserves again; the same event redelivered, or a late one for the old
		// reservation, must not free the new hold.
		UUID secondReservation = UUID.randomUUID();
		holdService.reserve(spot, "driver-1", secondReservation);
		publish("reservation.expired", eventId, spot, firstReservation, null);
		publish("reservation.cancelled", UUID.randomUUID(), spot, firstReservation, null);
		UUID marker = UUID.randomUUID();
		publish("reservation.cancelled", marker, UUID.randomUUID(), UUID.randomUUID(), null);
		await().atMost(Duration.ofSeconds(10)).until(() -> processedEventRepository.existsById(marker));

		assertThat(spotRepository.findById(spot).orElseThrow().getStatus()).isEqualTo(SpotStatus.RESERVED);
	}

	@Test
	void completedSessionFreesTheSpot() {
		UUID spot = newSpot();
		UUID reservation = UUID.randomUUID();
		UUID session = UUID.randomUUID();
		holdService.reserve(spot, "driver-1", reservation);
		holdService.occupy(spot, "driver-1", session);

		publish("session.completed", UUID.randomUUID(), spot, reservation, session);

		awaitStatus(spot, SpotStatus.AVAILABLE);
	}

	private UUID newSpot() {
		UUID location = locationService.create("Event garage", "Test street", 46.77, 23.59).location().getId();
		return spotService.addSpots(location, 0, "E", 1, SpotType.STANDARD, false, false).getFirst().getId();
	}

	/** Publishes the JSON reservation-service would send (extra fields are ignored by the listener). */
	private void publish(String routingKey, UUID eventId, UUID spotId, UUID reservationId, UUID sessionId) {
		Map<String, Object> event = new java.util.HashMap<>(Map.of("eventId", eventId, "occurredAt", "2026-10-26T10:00:00Z",
				"spotId", spotId, "driverId", "driver-1", "spotCode", "E1"));
		event.put("reservationId", reservationId);
		event.put("sessionId", sessionId);
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, routingKey, event);
	}

	private void awaitStatus(UUID spot, SpotStatus status) {
		await().atMost(Duration.ofSeconds(10))
			.until(() -> spotRepository.findById(spot).orElseThrow().getStatus() == status);
	}

}
