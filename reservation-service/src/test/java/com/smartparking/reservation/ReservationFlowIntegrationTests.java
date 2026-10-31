package com.smartparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.LocationInfo;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.client.ParkingServiceUnavailableException;
import com.smartparking.reservation.client.SpotConflictException;
import com.smartparking.reservation.messaging.MessagingConfig;
import com.smartparking.reservation.pricing.PricingRule;
import com.smartparking.reservation.reservation.service.ReservationService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A driver's journey through the REST API against real PostgreSQL and RabbitMQ. parking-service is
 * replaced by a mock that behaves like a free spot; published events are read back from the broker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReservationFlowIntegrationTests {

	private static final UUID LOCATION = UUID.randomUUID();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ReservationService reservationService;

	@MockitoBean
	private ParkingClient parkingClient;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	private static PublishedEvents events;

	private UUID spot;
	private String driver;

	@BeforeEach
	void freeSpot() {
		spot = UUID.randomUUID();
		driver = "driver-" + UUID.randomUUID();
		HeldSpot held = new HeldSpot(spot, "A1", LOCATION, "Central Garage", "RESERVED", driver);
		when(parkingClient.reserveSpot(eq(spot), eq(driver), any())).thenReturn(held);
		when(parkingClient.occupySpot(eq(spot), eq(driver), any())).thenReturn(held);
		if (events == null) {
			events = new PublishedEvents(rabbitTemplate);
		}
		when(parkingClient.getLocation(LOCATION))
			.thenReturn(new LocationInfo(LOCATION, "Central Garage", PricingRule.hourly(new BigDecimal("6.00"))));
	}

	@Test
	void reserveParkLeave() throws Exception {
		String reservationId = id(reserve(spot, driver)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.spotCode").value("A1"))
			.andExpect(jsonPath("$.locationName").value("Central Garage")));
		String created = events.await("reservation.created", reservationId);
		assertThat(PublishedEvents.<String>read(created, "$.spotCode")).isEqualTo("A1");
		assertThat(created).doesNotContain("routingKey");

		reserve(UUID.randomUUID(), driver)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You already have a reservation or a session running."));
		current().andExpect(jsonPath("$.reservation.id").value(reservationId));

		String sessionId = id(json(post("/api/v1/parking-sessions"),
				"{\"spotId\":\"%s\",\"reservationId\":\"%s\",\"driverId\":\"%s\"}".formatted(spot, reservationId, driver))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE")));

		mvc.perform(get("/api/v1/reservations/{id}", reservationId)).andExpect(jsonPath("$.status").value("COMPLETED"));
		current()
			.andExpect(jsonPath("$.reservation").isEmpty())
			.andExpect(jsonPath("$.session.id").value(sessionId))
			.andExpect(jsonPath("$.session.cost").value(6.0));

		mvc.perform(post("/api/v1/parking-sessions/{id}/end", sessionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.cost").value(6.0))
			.andExpect(jsonPath("$.paymentStatus").value("PENDING"));
		String completed = events.await("session.completed", sessionId);
		assertThat(PublishedEvents.<Double>read(completed, "$.cost")).isEqualTo(6.0);
		assertThat(PublishedEvents.<String>read(completed, "$.reservationId")).isEqualTo(reservationId);
		verify(parkingClient, never()).releaseSpotIfHeld(any(), any());

		// payment-service reports the outcome; the receipt picks it up.
		String paymentId = UUID.randomUUID().toString();
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "payment.failed",
				Map.of("eventId", UUID.randomUUID(), "paymentId", paymentId, "sessionId", sessionId, "amount", 6.0));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/v1/parking-sessions/{id}", sessionId))
			.andExpect(jsonPath("$.paymentStatus").value("FAILED"))
			.andExpect(jsonPath("$.paymentId").value(paymentId)));

		mvc.perform(post("/api/v1/parking-sessions/{id}/end", sessionId)).andExpect(status().isConflict());
		current()
			.andExpect(jsonPath("$.session").isEmpty())
			.andExpect(jsonPath("$.lastCompleted.id").value(sessionId));
	}

	@Test
	void cancellingFreesTheSpot() throws Exception {
		String reservationId = id(reserve(spot, driver));

		mvc.perform(delete("/api/v1/reservations/{id}", reservationId)).andExpect(status().isNoContent());

		// parking-service frees the spot from the event, not from a call.
		events.await("reservation.cancelled", reservationId);
		verify(parkingClient, never()).releaseSpotIfHeld(any(), any());
		mvc.perform(get("/api/v1/reservations/{id}", reservationId))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelReason").value("DRIVER"));
		mvc.perform(delete("/api/v1/reservations/{id}", reservationId)).andExpect(status().isConflict());
	}

	@Test
	void aClosedSpotCancelsItsReservation() throws Exception {
		String reservationId = id(reserve(spot, driver));
		// A closure for another spot (e.g. one the reservation already moved away from) changes nothing.
		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "spot.closed",
				Map.of("eventId", UUID.randomUUID(), "spotId", UUID.randomUUID(), "reservationId", reservationId));

		rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, "spot.closed",
				Map.of("eventId", UUID.randomUUID(), "spotId", spot, "reservationId", reservationId, "spotCode", "A1"));

		String cancelled = events.await("reservation.cancelled", reservationId);
		assertThat(PublishedEvents.<String>read(cancelled, "$.reason")).isEqualTo("SPOT_CLOSED");
		mvc.perform(get("/api/v1/reservations/{id}", reservationId))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelReason").value("SPOT_CLOSED"));
		current().andExpect(jsonPath("$.reservation").isEmpty());
	}

	@Test
	void movingAndExtendingAReservation() throws Exception {
		String reservationId = id(reserve(spot, driver));
		String expiresAt = read(mvc.perform(get("/api/v1/reservations/{id}", reservationId)), "$.expiresAt");
		UUID other = UUID.randomUUID();
		when(parkingClient.reserveSpot(eq(other), eq(driver), eq(UUID.fromString(reservationId))))
			.thenReturn(new HeldSpot(other, "B2", LOCATION, "Central Garage", "RESERVED", driver));

		json(patch("/api/v1/reservations/{id}", reservationId), "{\"spotId\":\"%s\"}".formatted(other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.spotId").value(other.toString()))
			.andExpect(jsonPath("$.spotCode").value("B2"))
			.andExpect(jsonPath("$.expiresAt").value(expiresAt));
		// parking-service frees the previous spot from the event, which names it.
		String moved = events.await("reservation.changed", reservationId);
		assertThat(PublishedEvents.<String>read(moved, "$.previousSpotId")).isEqualTo(spot.toString());
		assertThat(PublishedEvents.<String>read(moved, "$.previousSpotCode")).isEqualTo("A1");
		verify(parkingClient, never()).releaseSpotIfHeld(any(), any());

		json(patch("/api/v1/reservations/{id}", reservationId), "{\"spotId\":\"%s\"}".formatted(other))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Your reservation is already for B2."));

		mvc.perform(post("/api/v1/reservations/{id}/extend", reservationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.extended").value(true))
			.andExpect(jsonPath("$.expiresAt").value(Instant.parse(expiresAt).plus(Duration.ofMinutes(15)).toString()));
		mvc.perform(post("/api/v1/reservations/{id}/extend", reservationId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You have already extended this reservation once."));
	}

	@Test
	void aReservationThatEndedCannotBeMoved() throws Exception {
		String reservationId = id(reserve(spot, driver));
		mvc.perform(delete("/api/v1/reservations/{id}", reservationId)).andExpect(status().isNoContent());

		json(patch("/api/v1/reservations/{id}", reservationId), "{\"spotId\":\"%s\"}".formatted(UUID.randomUUID()))
			.andExpect(status().isConflict());
		// Rejected before asking parking-service (the one call is the original reservation), so nothing to give back.
		verify(parkingClient, times(1)).reserveSpot(any(), any(), eq(UUID.fromString(reservationId)));
		verify(parkingClient, never()).releaseSpotIfHeld(any(), any());
	}

	@Test
	void overdueReservationsExpire() throws Exception {
		String reservationId = id(reserve(spot, driver));

		reservationService.expireOverdue(Instant.now().plus(Duration.ofMinutes(16)));

		events.await("reservation.expired", reservationId);
		mvc.perform(get("/api/v1/reservations/{id}", reservationId)).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	@Test
	void aSpotThatIsNotFreeIsAConflict() throws Exception {
		when(parkingClient.reserveSpot(eq(spot), any(), any())).thenThrow(new SpotConflictException("Spot A1 is occupied, so it can't be reserved."));

		reserve(spot, driver)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Spot A1 is occupied, so it can't be reserved."));
	}

	@Test
	void parkingServiceDownIs503() throws Exception {
		when(parkingClient.reserveSpot(eq(spot), any(), any())).thenThrow(new ParkingServiceUnavailableException("timeout", null));

		reserve(spot, driver).andExpect(status().isServiceUnavailable());
	}

	private ResultActions reserve(UUID spotId, String driverId) throws Exception {
		return json(post("/api/v1/reservations"), "{\"spotId\":\"%s\",\"driverId\":\"%s\"}".formatted(spotId, driverId));
	}

	private ResultActions current() throws Exception {
		return mvc.perform(get("/api/v1/reservations/current").param("driverId", driver)).andExpect(status().isOk());
	}

	private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String id(ResultActions result) throws Exception {
		return read(result, "$.id");
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
