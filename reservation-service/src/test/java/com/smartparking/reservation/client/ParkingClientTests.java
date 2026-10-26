package com.smartparking.reservation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartparking.reservation.pricing.PricingStrategyType;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** parking-service's answers become this service's exceptions, with parking-service's message kept. */
class ParkingClientTests {

	private static final UUID SPOT = UUID.fromString("00000000-0000-0000-0000-000000001001");
	private static final UUID HOLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://parking");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final ParkingClient client = new ParkingClient(builder.build());

	@Test
	void reservesASpot() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"driverId\":\"driver-1\",\"holdId\":\"" + HOLD + "\"}"))
			.andRespond(withSuccess("""
					{"spotId":"%s","spotCode":"A1","locationId":"%s","locationName":"Central Garage","status":"RESERVED","heldBy":"driver-1"}"""
				.formatted(SPOT, UUID.randomUUID()), MediaType.APPLICATION_JSON));

		HeldSpot spot = client.reserveSpot(SPOT, "driver-1", HOLD);

		assertThat(spot.spotCode()).isEqualTo("A1");
		assertThat(spot.locationName()).isEqualTo("Central Garage");
	}

	@Test
	void conflictKeepsParkingServicesMessage() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve"))
			.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body("{\"status\":409,\"title\":\"Conflict\",\"detail\":\"Spot A1 is reserved, so it can't be reserved.\"}"));

		assertThatThrownBy(() -> client.reserveSpot(SPOT, "driver-1", HOLD))
			.isInstanceOf(SpotConflictException.class)
			.hasMessage("Spot A1 is reserved, so it can't be reserved.");
	}

	@Test
	void releaseIfHeldIgnoresASpotThatAlreadyMovedOn() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/release"))
			.andExpect(content().json("{\"holdId\":\"" + HOLD + "\"}"))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		client.releaseSpotIfHeld(SPOT, HOLD);
		server.verify();
	}

	@Test
	void unknownSpotIsNotFound() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/occupy")).andRespond(withResourceNotFound());

		assertThatThrownBy(() -> client.occupySpot(SPOT, "driver-1", HOLD)).isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	void serverErrorMeansUnavailable() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/occupy")).andRespond(withServerError());

		assertThatThrownBy(() -> client.occupySpot(SPOT, "driver-1", HOLD)).isInstanceOf(ParkingServiceUnavailableException.class);
	}

	@Test
	void readsTheLocationsPricingRule() {
		UUID location = UUID.randomUUID();
		server.expect(requestTo("http://parking/api/v1/parking-locations/" + location))
			.andRespond(withSuccess("""
					{"id":"%s","name":"Central Garage","address":"x","latitude":1,"longitude":2,"totalSpots":3,"freeSpots":3,
					 "pricing":{"strategy":"TIERED","firstHourRate":8.00,"laterHourRate":4.00}}""".formatted(location),
					MediaType.APPLICATION_JSON));

		LocationInfo info = client.getLocation(location);

		assertThat(info.pricing().strategy()).isEqualTo(PricingStrategyType.TIERED);
		assertThat(info.pricing().laterHourRate()).isEqualByComparingTo("4.00");
	}

}
