package com.smartparking.reservation.common;

import com.smartparking.reservation.client.ParkingCircuitOpenException;
import com.smartparking.reservation.client.ParkingServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns domain and persistence errors into RFC 7807 problem responses.
 * Extending {@link ResponseEntityExceptionHandler} also gives Spring MVC's own errors
 * (failed {@code @Valid}, malformed JSON, unknown path) the same ProblemDetail shape.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** A business rule refused the action, e.g. an illegal state change or an already finished entity. */
	@ExceptionHandler(IllegalStateException.class)
	public ProblemDetail handleConflict(IllegalStateException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	/**
	 * Two requests changed the same row at once: a {@code @Version} check or a unique index
	 * rejected the slower one.
	 */
	@ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
	public ProblemDetail handleConcurrentChange(RuntimeException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", "Someone else changed this a moment ago. Reload and try again.");
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ProblemDetail handleNotFound(EntityNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	/** A synchronous call to parking-service failed or timed out; the driver can simply try again. */
	@ExceptionHandler(ParkingServiceUnavailableException.class)
	public ProblemDetail handleParkingUnavailable(ParkingServiceUnavailableException ex) {
		log.warn("parking-service call failed: {}", ex.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
				"Spot On can't reach the car park system right now. Try again in a moment.");
	}

	/**
	 * The circuit breaker stopped calling parking-service after repeated failures. The driver is told
	 * how long to wait; {@code Retry-After} carries the same time for other clients.
	 */
	@ExceptionHandler(ParkingCircuitOpenException.class)
	public ResponseEntity<ProblemDetail> handleParkingCircuitOpen(ParkingCircuitOpenException ex) {
		long seconds = Math.max(1, ex.getRetryAfter().toSeconds());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
			.body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
					"The car park system isn't answering, so Spot On can't reserve or start parking right now. "
							+ "Try again in " + seconds + " seconds."));
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
