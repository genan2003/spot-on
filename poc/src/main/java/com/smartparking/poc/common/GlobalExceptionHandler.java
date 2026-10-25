package com.smartparking.poc.common;

import com.smartparking.poc.spot.IllegalSpotStateTransitionException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain and persistence errors into RFC 7807 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Illegal spot transition (State), an action on a finished reservation or session. */
    @ExceptionHandler({IllegalSpotStateTransitionException.class, IllegalStateException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    /**
     * Two requests changed the same spot at once: the {@code @Version} check or the unique index
     * on active reservations rejected the slower one.
     */
    @ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConcurrentChange(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Someone else changed this spot a moment ago. Pick another spot or try again.");
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
