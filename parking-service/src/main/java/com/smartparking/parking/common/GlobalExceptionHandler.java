package com.smartparking.parking.common;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns domain and persistence errors into RFC 7807 problem responses.
 * Extending {@link ResponseEntityExceptionHandler} also gives Spring MVC's own errors
 * (failed {@code @Valid}, malformed JSON, unknown path) the same ProblemDetail shape.
 */
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

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
