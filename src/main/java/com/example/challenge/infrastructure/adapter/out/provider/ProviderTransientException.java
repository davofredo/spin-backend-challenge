package com.example.challenge.infrastructure.adapter.out.provider;

/**
 * Signals a provider failure that is worth retrying: connection errors, timeouts and 5xx answers.
 * <p>
 * It is the only exception listed in the retry and circuit-breaker {@code record-exceptions}
 * configuration, which is how a 4xx rejection stays out of both mechanisms: a rejection is returned
 * as a value, never thrown.
 */
public class ProviderTransientException extends RuntimeException {

    public ProviderTransientException(String message) {
        super(message);
    }

    public ProviderTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
