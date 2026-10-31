package com.smartparking.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartparking.parking.messaging.MessagingConfig;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.event.SpotStatusChanged;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The parking-service REST API end to end, against real PostgreSQL. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ParkingApiIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@MockitoSpyBean
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Test
	void adminSetsUpALocationWithSpotsAndPricing() throws Exception {
		String locationId = createLocation("Central Garage");

		mvc.perform(get("/api/v1/parking-locations/{id}", locationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pricing.strategy").value("HOURLY"))
			.andExpect(jsonPath("$.pricing.hourlyRate").value(5.0))
			.andExpect(jsonPath("$.totalSpots").value(0));

		addSpots(locationId, "A", 3);
		send(post("/api/v1/parking-locations/{id}/spots", locationId), """
				{"level":0,"zone":"A","count":2,"type":"LARGE","evCharging":true,"accessible":false}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].code").value("A4"))
			.andExpect(jsonPath("$[1].code").value("A5"));

		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"TIERED","bands":[{"fromHour":0,"hourlyRate":8},{"fromHour":1,"hourlyRate":4}],"hourlyRate":99}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pricing.strategy").value("TIERED"))
			.andExpect(jsonPath("$.pricing.bands.length()").value(2))
			.andExpect(jsonPath("$.pricing.bands[1].hourlyRate").value(4))
			.andExpect(jsonPath("$.pricing.hourlyRate").doesNotExist())
			.andExpect(jsonPath("$.pricing.billingIncrementMinutes").value(60))
			.andExpect(jsonPath("$.pricing.gracePeriodMinutes").value(0))
			.andExpect(jsonPath("$.totalSpots").value(5))
			.andExpect(jsonPath("$.freeSpots").value(5));

		mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(5))
			.andExpect(jsonPath("$[0].code").value("A1"))
			.andExpect(jsonPath("$[0].status").value("AVAILABLE"));
	}

	@Test
	void pricingRuleWithAMissingRateIsRejected() throws Exception {
		String locationId = createLocation("Rate check");
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"NIGHT_WEEKEND","dayRate":8}""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void pricingExtrasAreStoredWithTheRule() throws Exception {
		String locationId = createLocation("Extras");
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"NIGHT_WEEKEND","dayRate":6,"nightWeekendRate":2,"nightStart":"22:00","nightEnd":"06:30",
				 "billingIncrementMinutes":15,"gracePeriodMinutes":10,"dailyCap":40,
				 "surcharges":{"compact":-10,"large":20,"motorcycle":-50,"evCharging":15}}""")
			.andExpect(status().isOk());

		mvc.perform(get("/api/v1/parking-locations/{id}", locationId))
			.andExpect(jsonPath("$.pricing.nightStart").value("22:00"))
			.andExpect(jsonPath("$.pricing.nightEnd").value("06:30"))
			.andExpect(jsonPath("$.pricing.billingIncrementMinutes").value(15))
			.andExpect(jsonPath("$.pricing.gracePeriodMinutes").value(10))
			.andExpect(jsonPath("$.pricing.dailyCap").value(40))
			.andExpect(jsonPath("$.pricing.surcharges.large").value(20))
			.andExpect(jsonPath("$.pricing.surcharges.motorcycle").value(-50))
			.andExpect(jsonPath("$.pricing.bands").doesNotExist());
	}

	@Test
	void pricingRulesThatMakeNoSenseAreRejected() throws Exception {
		String locationId = createLocation("Bad rules");
		// Bands must start at hour 0, in order.
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"TIERED","bands":[{"fromHour":1,"hourlyRate":4}]}""")
			.andExpect(status().isBadRequest());
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"TIERED","bands":[{"fromHour":0,"hourlyRate":8},{"fromHour":0,"hourlyRate":4}]}""")
			.andExpect(status().isBadRequest());
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"HOURLY","hourlyRate":6,"billingIncrementMinutes":7}""")
			.andExpect(status().isBadRequest());
		send(put("/api/v1/parking-locations/{id}/pricing-rule", locationId), """
				{"strategy":"HOURLY","hourlyRate":6,"surcharges":{"compact":0,"large":500,"motorcycle":0,"evCharging":0}}""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void closingAReservedSpotAnnouncesItSoTheReservationIsCancelled() throws Exception {
		String locationId = createLocation("Closing reserved");
		String spotId = addSpots(locationId, "R", 1);
		String reservation = UUID.randomUUID().toString();
		hold(spotId, "reserve", "driver-7", reservation).andExpect(status().isOk());
		// A queue of our own on the exchange, to read what parking-service announces.
		Queue listener = amqpAdmin.declareQueue();
		amqpAdmin.declareBinding(new Binding(listener.getName(), Binding.DestinationType.QUEUE, MessagingConfig.EXCHANGE,
				MessagingConfig.SPOT_CLOSED, null));

		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spotId), "{\"status\":\"OUT_OF_SERVICE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
			.andExpect(jsonPath("$.heldBy").doesNotExist());

		// Read as plain JSON, the way reservation-service reads it into its own record.
		Message message = rabbitTemplate.receive(listener.getName(), 10_000);
		assertThat(message).isNotNull();
		String closed = new String(message.getBody(), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(closed, "$.spotId")).isEqualTo(spotId);
		assertThat((String) JsonPath.read(closed, "$.spotCode")).isEqualTo("R1");
		assertThat((String) JsonPath.read(closed, "$.reservationId")).isEqualTo(reservation);
		assertThat((String) JsonPath.read(closed, "$.driverId")).isEqualTo("driver-7");

		// The reservation's own cancel event, arriving later, can't reopen the closed spot.
		release(spotId, reservation).andExpect(status().isConflict());
	}

	@Test
	void anOccupiedSpotCannotBeClosed() throws Exception {
		String locationId = createLocation("Closing occupied");
		String spotId = addSpots(locationId, "O", 1);
		hold(spotId, "occupy", "driver-8", UUID.randomUUID().toString()).andExpect(status().isOk());

		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spotId), "{\"status\":\"OUT_OF_SERVICE\"}")
			.andExpect(status().isConflict());
	}

	@Test
	void adminEditsTheSpotsProperties() throws Exception {
		String locationId = createLocation("Editing spots");
		String first = addSpots(locationId, "E", 3);
		String body = mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId)).andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(body, "$[1].id");
		String third = JsonPath.read(body, "$[2].id");

		// One spot: give it a charger; what isn't sent stays as it was.
		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, first), "{\"evCharging\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evCharging").value(true))
			.andExpect(jsonPath("$.accessible").value(false))
			.andExpect(jsonPath("$.type").value("STANDARD"))
			.andExpect(jsonPath("$.status").value("AVAILABLE"));
		// Live clients see the new property too.
		verify(messagingTemplate, timeout(5000)).convertAndSend(eq("/topic/spot-status"),
				argThat((Object e) -> e instanceof SpotStatusChanged c && c.spotId().toString().equals(first) && c.evCharging()));

		// Several spots at once.
		send(patch("/api/v1/parking-locations/{id}/spots", locationId), """
				{"spotIds":["%s","%s"],"type":"LARGE","accessible":true}""".formatted(third, second))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].code").value("E2"))
			.andExpect(jsonPath("$[0].type").value("LARGE"))
			.andExpect(jsonPath("$[1].accessible").value(true))
			.andExpect(jsonPath("$[1].evCharging").value(false));

		// Status and properties in one request.
		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, first), "{\"status\":\"OUT_OF_SERVICE\",\"type\":\"COMPACT\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
			.andExpect(jsonPath("$.type").value("COMPACT"));
	}

	@Test
	void spotEditsThatMakeNoSenseAreRejected() throws Exception {
		String locationId = createLocation("Bad edits");
		String spot = addSpots(locationId, "X", 1);
		String elsewhere = addSpots(createLocation("Other garage"), "Y", 1);

		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spot), "{}").andExpect(status().isBadRequest());
		send(patch("/api/v1/parking-locations/{id}/spots", locationId), "{\"spotIds\":[],\"evCharging\":true}")
			.andExpect(status().isBadRequest());
		send(patch("/api/v1/parking-locations/{id}/spots", locationId), "{\"spotIds\":[\"%s\"]}".formatted(spot))
			.andExpect(status().isBadRequest());
		// A spot of another car park can't be edited from this one.
		send(patch("/api/v1/parking-locations/{id}/spots", locationId), "{\"spotIds\":[\"%s\",\"%s\"],\"evCharging\":true}"
				.formatted(spot, elsewhere))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId)).andExpect(jsonPath("$[0].evCharging").value(false));
	}

	@Test
	void adminDeletesSpotsAndNumberingContinues() throws Exception {
		String locationId = createLocation("Deleting spots");
		addSpots(locationId, "D", 3);
		String body = mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId)).andReturn().getResponse().getContentAsString();
		String d2 = JsonPath.read(body, "$[1].id");
		String d3 = JsonPath.read(body, "$[2].id");

		mvc.perform(delete("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, d2)).andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, d2)).andExpect(status().isNotFound());

		// D1 and D3 are left; the next spot is D4, not a second D3.
		send(post("/api/v1/parking-locations/{id}/spots", locationId), """
				{"level":0,"zone":"D","count":1,"type":"STANDARD","evCharging":false,"accessible":false}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].code").value("D4"));

		// A spot a driver is using can't be deleted.
		hold(d3, "reserve", "driver-9", UUID.randomUUID().toString()).andExpect(status().isOk());
		mvc.perform(delete("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, d3))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Spot D3 is reserved. Wait until it is free, or close it first if it is only reserved."));
		// Nor can the car park, and the answer says which spots are in use.
		mvc.perform(delete("/api/v1/parking-locations/{id}", locationId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(
					"Deleting spots can't be deleted while drivers use D3. Wait until those spots are free; reserved ones you can close first."));
	}

	@Test
	void adminClosesAndReopensASpot() throws Exception {
		String locationId = createLocation("Closing test");
		String spotId = addSpots(locationId, "B", 2);

		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spotId), "{\"status\":\"OUT_OF_SERVICE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

		mvc.perform(get("/api/v1/parking-locations/{id}/availability", locationId))
			.andExpect(jsonPath("$.AVAILABLE").value(1))
			.andExpect(jsonPath("$.OUT_OF_SERVICE").value(1))
			.andExpect(jsonPath("$.OCCUPIED").value(0));

		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spotId), "{\"status\":\"OCCUPIED\"}")
			.andExpect(status().isBadRequest());
		send(patch("/api/v1/parking-locations/{id}/spots/{spotId}", locationId, spotId), "{\"status\":\"AVAILABLE\"}")
			.andExpect(status().isOk());
	}

	@Test
	void driverHoldsAreCheckedByTheSpotState() throws Exception {
		String locationId = createLocation("Hold test");
		String spotId = addSpots(locationId, "C", 1);
		String reservation = UUID.randomUUID().toString();
		String session = UUID.randomUUID().toString();

		hold(spotId, "reserve", "driver-1", reservation)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESERVED"))
			.andExpect(jsonPath("$.locationName").value("Hold test"))
			.andExpect(jsonPath("$.spotCode").value("C1"));
		hold(spotId, "reserve", "driver-2", UUID.randomUUID().toString()).andExpect(status().isConflict());
		hold(spotId, "occupy", "driver-2", UUID.randomUUID().toString())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Spot C1 is reserved by another driver."));

		hold(spotId, "occupy", "driver-1", session)
			.andExpect(jsonPath("$.status").value("OCCUPIED"))
			.andExpect(jsonPath("$.type").value("STANDARD"))
			.andExpect(jsonPath("$.evCharging").value(false));
		// The admin sees which car is parked on the bay, until it is freed.
		mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId))
			.andExpect(jsonPath("$[?(@.id == '%s')].licencePlate".formatted(spotId)).value("CJ 07 ABC"));
		mvc.perform(delete("/api/v1/parking-locations/{id}", locationId)).andExpect(status().isConflict());

		// The session is the hold now: releasing the old reservation must not free the parked car.
		release(spotId, reservation)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Spot C1 is no longer held by that reservation or session."));
		release(spotId, session).andExpect(jsonPath("$.status").value("AVAILABLE"));
		mvc.perform(get("/api/v1/parking-locations/{id}/spots", locationId))
			.andExpect(jsonPath("$[?(@.id == '%s')].licencePlate".formatted(spotId)).value(org.hamcrest.Matchers.contains((Object) null)));

		// Observer: every committed change went through RabbitMQ to the STOMP topic.
		verify(messagingTemplate, timeout(5000)).convertAndSend(eq("/topic/spot-status"),
				argThat((Object e) -> e instanceof SpotStatusChanged c && c.status() == SpotStatus.OCCUPIED && "driver-1".equals(c.heldBy())
						&& "CJ 07 ABC".equals(c.licencePlate())));

		mvc.perform(delete("/api/v1/parking-locations/{id}", locationId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/parking-locations/{id}", locationId)).andExpect(status().isNotFound());
	}

	@Test
	void unknownSpotIs404() throws Exception {
		hold("00000000-0000-0000-0000-000000000000", "reserve", "driver-1", UUID.randomUUID().toString())
			.andExpect(status().isNotFound());
	}

	private String createLocation(String name) throws Exception {
		String body = send(post("/api/v1/parking-locations"), """
				{"name":"%s","address":"Strada Memorandumului 21","latitude":46.77,"longitude":23.59}""".formatted(name))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		assertThat(id).isNotBlank();
		return id;
	}

	/** Adds spots to a zone and returns the id of the first one. */
	private String addSpots(String locationId, String zone, int count) throws Exception {
		String body = send(post("/api/v1/parking-locations/{id}/spots", locationId), """
				{"level":0,"zone":"%s","count":%d,"type":"STANDARD","evCharging":false,"accessible":false}""".formatted(zone, count))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.length()").value(count))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$[0].id");
	}

	private ResultActions hold(String spotId, String action, String driverId, String holdId) throws Exception {
		// reservation-service sends the car's plate with occupy only.
		String plate = action.equals("occupy") ? ",\"licencePlate\":\"CJ 07 ABC\"" : "";
		return send(post("/api/v1/spots/{spotId}/" + action, spotId), """
				{"driverId":"%s","holdId":"%s"%s}""".formatted(driverId, holdId, plate));
	}

	private ResultActions release(String spotId, String holdId) throws Exception {
		return send(post("/api/v1/spots/{spotId}/release", spotId), "{\"holdId\":\"" + holdId + "\"}");
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
	}

}
