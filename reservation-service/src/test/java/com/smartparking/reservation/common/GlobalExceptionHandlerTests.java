package com.smartparking.reservation.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.reservation.client.ParkingCircuitOpenException;
import com.smartparking.reservation.client.ParkingServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Every error leaves the service as an RFC 7807 ProblemDetail with the right status. */
class GlobalExceptionHandlerTests {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void notFoundBecomes404() throws Exception {
		mvc.perform(get("/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Not found"))
			.andExpect(jsonPath("$.detail").value("Nothing here"));
	}

	@Test
	void brokenRuleBecomes409() throws Exception {
		mvc.perform(get("/broken-rule"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Already ended"));
	}

	@Test
	void concurrentChangeBecomes409() throws Exception {
		mvc.perform(get("/concurrent-change"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void invalidBodyBecomes400() throws Exception {
		mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void parkingServiceDownBecomes503() throws Exception {
		mvc.perform(get("/parking-down"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.detail").value("Spot On can't reach the car park system right now. Try again in a moment."));
	}

	@Test
	void openCircuitBecomes503WithRetryAfter() throws Exception {
		mvc.perform(get("/circuit-open"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().string("Retry-After", "30"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("The car park system isn't answering, so Spot On can't reserve or start "
					+ "parking right now. Try again in 30 seconds."));
	}

	record Body(@NotBlank String name) {
	}

	@RestController
	static class FailingController {

		@GetMapping("/not-found")
		void notFound() {
			throw new EntityNotFoundException("Nothing here");
		}

		@GetMapping("/broken-rule")
		void brokenRule() {
			throw new IllegalStateException("Already ended");
		}

		@GetMapping("/concurrent-change")
		void concurrentChange() {
			throw new OptimisticLockingFailureException("version mismatch");
		}

		@GetMapping("/parking-down")
		void parkingDown() {
			throw new ParkingServiceUnavailableException("parking-service answered 500.", null);
		}

		@GetMapping("/circuit-open")
		void circuitOpen() {
			throw new ParkingCircuitOpenException(Duration.ofSeconds(30), null);
		}

		@PostMapping("/validated")
		void validated(@Valid @RequestBody Body body) {
		}

	}

}
