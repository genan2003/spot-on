package com.smartparking.payment.common;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Time and randomness are injected rather than read directly, so tests can make a payment fail on purpose. */
@Configuration
public class TimeAndRandomConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * {@link Random} rather than {@code RandomGenerator.getDefault()}: the default algorithm lives in
	 * the {@code jdk.random} module, which the slim JRE in the Docker image doesn't include.
	 */
	@Bean
	RandomGenerator random() {
		return new Random();
	}

}
