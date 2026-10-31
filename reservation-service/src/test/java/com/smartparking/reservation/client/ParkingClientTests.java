package com.smartparking.reservation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import com.smartparking.reservation.pricing.PricingStrategyType;
import com.smartparking.reservation.pricing.Bay;
import com.smartparking.reservation.pricing.BaySurcharges;
import com.smartparking.reservation.pricing.BayType;
import com.smartparking.reservation.pricing.PriceBand;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * parking-service's answers become this service's exceptions, with parking-service's message kept;
 * failed calls are retried and repeated failures open the circuit breaker (same settings as application.yml).
 */
class ParkingClientTests {

	private static final UUID SPOT = UUID.fromString("00000000-0000-0000-0000-000000001001");
	private static final UUID HOLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://parking");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
		.slidingWindowSize(10)
		.minimumNumberOfCalls(5)
		.failureRateThreshold(50)
		.waitDurationInOpenState(Duration.ofSeconds(30))
		.recordExceptions(ParkingServiceUnavailableException.class)
		.build());
	private final Retry retry = Retry.of("test", RetryConfig.custom()
		.maxAttempts(3)
		.waitDuration(Duration.ofMillis(1))
		.retryExceptions(ParkingServiceUnavailableException.class)
		.build());
	private final ParkingClient client = new ParkingClient(builder.build(), circuitBreaker, retry);

	@Test
	void reservesASpot() {
		server.expect(requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"driverId\":\"driver-1\",\"holdId\":\"" + HOLD + "\"}"))
			.andRespond(withSuccess("""
					{"spotId":"%s","spotCode":"A1","locationId":"%s","locationName":"Central Garage","status":"RESERVED","heldBy":"driver-1",
					 "type":"LARGE","evCharging":true}"""
				.formatted(SPOT, UUID.randomUUID()), MediaType.APPLICATION_JSON));

		HeldSpot spot = client.reserveSpot(SPOT, "driver-1", HOLD);

		assertThat(spot.spotCode()).isEqualTo("A1");
		assertThat(spot.locationName()).isEqualTo("Central Garage");
		assertThat(spot.bay()).isEqualTo(new Bay(BayType.LARGE, true));
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
	void serverErrorMeansUnavailableAfterThreeAttempts() {
		server.expect(times(3), requestTo("http://parking/api/v1/spots/" + SPOT + "/occupy")).andRespond(withServerError());

		assertThatThrownBy(() -> client.occupySpot(SPOT, "driver-1", HOLD)).isInstanceOf(ParkingServiceUnavailableException.class);
		server.verify();
	}

	@Test
	void aFailedAttemptIsRetried() {
		server.expect(once(), requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve")).andRespond(withServerError());
		server.expect(once(), requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve"))
			.andRespond(withSuccess("""
					{"spotId":"%s","spotCode":"A1","locationId":"%s","locationName":"Central Garage","status":"RESERVED","heldBy":"driver-1",
					 "type":"STANDARD","evCharging":false}""".formatted(SPOT, UUID.randomUUID()), MediaType.APPLICATION_JSON));

		assertThat(client.reserveSpot(SPOT, "driver-1", HOLD).spotCode()).isEqualTo("A1");
		server.verify();
	}

	@Test
	void anAnswerIsNotRetriedAndKeepsTheCircuitClosed() {
		server.expect(times(6), requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve"))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		for (int i = 0; i < 6; i++) {
			assertThatThrownBy(() -> client.reserveSpot(SPOT, "driver-1", HOLD)).isInstanceOf(SpotConflictException.class);
		}
		server.verify();
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void repeatedFailuresOpenTheCircuitAndLaterCallsFailAtOnce() {
		// Two driver requests of three attempts each; the fifth failed call (the minimum) opens the circuit,
		// so the second request's last attempt is already refused without reaching parking-service.
		server.expect(times(5), requestTo("http://parking/api/v1/spots/" + SPOT + "/reserve")).andRespond(withServerError());
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> client.reserveSpot(SPOT, "driver-1", HOLD)).isInstanceOf(ParkingServiceUnavailableException.class);
		}
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		// The next request doesn't reach parking-service either: the open circuit answers itself.
		assertThatThrownBy(() -> client.reserveSpot(SPOT, "driver-1", HOLD))
			.isInstanceOfSatisfying(ParkingCircuitOpenException.class,
					open -> assertThat(open.getRetryAfter()).isEqualTo(Duration.ofSeconds(30)));
		server.verify();
	}

	@Test
	void readsTheLocationsPricingRule() {
		UUID location = UUID.randomUUID();
		server.expect(requestTo("http://parking/api/v1/parking-locations/" + location))
			.andRespond(withSuccess("""
					{"id":"%s","name":"Central Garage","address":"x","latitude":1,"longitude":2,"totalSpots":3,"freeSpots":3,
					 "pricing":{"strategy":"NIGHT_WEEKEND","dayRate":6.00,"nightWeekendRate":2.00,"nightStart":"22:00","nightEnd":"06:30",
					            "bands":[{"fromHour":0,"hourlyRate":8.00}],"billingIncrementMinutes":15,"gracePeriodMinutes":10,
					            "dailyCap":40.00,"surcharges":{"compact":-10,"large":20,"motorcycle":-50,"evCharging":15}}}""".formatted(location),
					MediaType.APPLICATION_JSON));

		LocationInfo info = client.getLocation(location);

		assertThat(info.pricing().strategy()).isEqualTo(PricingStrategyType.NIGHT_WEEKEND);
		assertThat(info.pricing().nightStart()).isEqualTo(LocalTime.of(22, 0));
		assertThat(info.pricing().nightEnd()).isEqualTo(LocalTime.of(6, 30));
		assertThat(info.pricing().bands()).containsExactly(new PriceBand(0, new BigDecimal("8.00")));
		assertThat(info.pricing().billingIncrementMinutes()).isEqualTo(15);
		assertThat(info.pricing().gracePeriodMinutes()).isEqualTo(10);
		assertThat(info.pricing().dailyCap()).isEqualByComparingTo("40.00");
		assertThat(info.pricing().surcharges()).isEqualTo(new BaySurcharges(-10, 20, -50, 15));
	}

}
