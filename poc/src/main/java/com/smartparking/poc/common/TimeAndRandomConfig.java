package com.smartparking.poc.common;

import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Time and randomness are injected rather than read directly, so tests and the demo can control
 * them (move the clock forward, make a payment fail on purpose). The demo profile has its own.
 */
@Configuration
@Profile("!demo")
public class TimeAndRandomConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator random() {
        return RandomGenerator.getDefault();
    }
}
