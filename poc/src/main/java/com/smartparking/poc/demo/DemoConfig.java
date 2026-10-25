package com.smartparking.poc.demo;

import java.time.Instant;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoConfig {

    /** Wednesday 4 March 2026, 10:00 in Cluj-Napoca: a weekday daytime, so day rates apply. */
    @Bean
    DemoClock clock() {
        return new DemoClock(Instant.parse("2026-03-04T08:00:00Z"));
    }

    /** Fixed seed: with the demo's 50% failure rate, the first payment succeeds and the second fails. */
    @Bean
    RandomGenerator random() {
        return new Random(1);
    }
}
