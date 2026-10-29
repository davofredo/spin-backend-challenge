package com.example.challenge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} the application layer uses to stamp transactions.
 * <p>
 * Time is a dependency rather than a static call so tests can fix the instant instead of asserting
 * against {@code Instant.now()}. UTC is the canonical zone: timestamps are stored and returned in
 * UTC and rendered as ISO-8601.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
