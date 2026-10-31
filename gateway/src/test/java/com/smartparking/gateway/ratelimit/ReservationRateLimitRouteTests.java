package com.smartparking.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The real route table: only {@code POST /api/v1/reservations} is limited, per driver. reservation-service isn't
 * running here (port 1 refuses connections), so a request that gets through ends in a gateway error, not a 429.
 */
@SpringBootTest(properties = {"RESERVATION_SERVICE_URL=http://localhost:1", "RESERVATION_RATE_LIMIT=3"})
class ReservationRateLimitRouteTests {

	@Autowired
	private ApplicationContext context;

	private WebTestClient client() {
		return WebTestClient.bindToApplicationContext(context).build();
	}

	@Test
	void theFourthReservationAttemptOfADriverIsRefused() {
		for (int i = 0; i < 3; i++) {
			assertThat(reserve("route-driver-1").getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		}
		var refused = reserve("route-driver-1");

		assertThat(refused.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refused.getResponseHeaders().getFirst("Retry-After")).isEqualTo("20");
		assertThat(reserve("route-driver-2").getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void otherReservationRequestsAreNotLimited() {
		for (int i = 0; i < 5; i++) {
			var current = client().get()
				.uri("/api/v1/reservations/current?driverId=route-driver-3")
				.header("X-Driver-Id", "route-driver-3")
				.exchange()
				.returnResult(String.class);
			assertThat(current.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		}
	}

	private org.springframework.test.web.reactive.server.FluxExchangeResult<String> reserve(String driver) {
		return client().post()
			.uri("/api/v1/reservations")
			.header("X-Driver-Id", driver)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"driverId\":\"" + driver + "\",\"spotId\":\"00000000-0000-0000-0000-000000000001\"}")
			.exchange()
			.returnResult(String.class);
	}

}
