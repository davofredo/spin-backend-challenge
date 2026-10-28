package com.example.challenge.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outcome of a provider execution, expressed in application terms.
 * <p>
 * The three cases mirror what the service can record: the provider approved the transaction, it
 * rejected it with its own code, or its answer is unknown (unreachable, timeout, server error,
 * malformed response or open circuit). Only {@link Approved} carries the provider identifier and
 * the resulting balance.
 */
public sealed interface ProviderResult
        permits ProviderResult.Approved, ProviderResult.Rejected, ProviderResult.Unavailable {

    record Approved(String providerTransactionId, BigDecimal balance, Instant executedAt)
            implements ProviderResult {
    }

    record Rejected(String code, String message) implements ProviderResult {
    }

    record Unavailable(String reason) implements ProviderResult {
    }

    static ProviderResult unavailable(String reason) {
        return new Unavailable(reason);
    }
}
