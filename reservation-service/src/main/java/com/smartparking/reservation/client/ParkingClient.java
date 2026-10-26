package com.smartparking.reservation.client;

import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Synchronous calls to parking-service, used where the driver needs an answer now: holding a spot
 * (the first guard against double reservations) and reading a location's pricing rule. Freeing a
 * spot is not one of them: that happens through events, except to undo a failed step.
 * parking-service's errors are turned back into this service's exceptions, keeping its message:
 * 409 → {@link SpotConflictException}, 404 → {@link EntityNotFoundException}, anything else → 503.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParkingClient {

	private final RestClient parkingRestClient;

	/** {@code reservationId} becomes the spot's hold; only an event or release naming it can free the spot. */
	public HeldSpot reserveSpot(UUID spotId, String driverId, UUID reservationId) {
		return hold(spotId, "reserve", Map.of("driverId", driverId, "holdId", reservationId));
	}

	/** {@code sessionId} becomes the spot's hold (it replaces the reservation's). */
	public HeldSpot occupySpot(UUID spotId, String driverId, UUID sessionId) {
		return hold(spotId, "occupy", Map.of("driverId", driverId, "holdId", sessionId));
	}

	/**
	 * Undoes a hold when the step after it failed (a compensating action). Normal releases are events.
	 * A refusal means the spot has already moved on to another hold, so it is only logged.
	 */
	public void releaseSpotIfHeld(UUID spotId, UUID holdId) {
		try {
			hold(spotId, "release", Map.of("holdId", holdId));
		}
		catch (SpotConflictException alreadyFree) {
			log.info("Spot {} not released for hold {}: {}", spotId, holdId, alreadyFree.getMessage());
		}
	}

	public LocationInfo getLocation(UUID locationId) {
		return call(() -> parkingRestClient.get()
			.uri("/api/v1/parking-locations/{id}", locationId)
			.retrieve()
			.body(LocationInfo.class));
	}

	private HeldSpot hold(UUID spotId, String action, Map<String, Object> body) {
		return call(() -> parkingRestClient.post()
			.uri("/api/v1/spots/{spotId}/{action}", spotId, action)
			.body(body)
			.retrieve()
			.body(HeldSpot.class));
	}

	private static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			String detail = detail(ex);
			if (ex.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new SpotConflictException(detail);
			}
			if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new EntityNotFoundException(detail);
			}
			throw new ParkingServiceUnavailableException("parking-service answered " + ex.getStatusCode().value() + ".", ex);
		}
		catch (ResourceAccessException ex) {
			throw new ParkingServiceUnavailableException("parking-service did not answer in time.", ex);
		}
	}

	private static String detail(RestClientResponseException ex) {
		try {
			ProblemDetail problem = ex.getResponseBodyAs(ProblemDetail.class);
			if (problem != null && problem.getDetail() != null) {
				return problem.getDetail();
			}
		}
		catch (RuntimeException unreadable) {
			// Not a ProblemDetail body; fall back to the status text.
		}
		return ex.getStatusText();
	}

}
