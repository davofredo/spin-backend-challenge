package com.example.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A well-formed transaction request that has not been validated against the business rules yet.
 * <p>
 * The identifier and creation timestamp are assigned by the caller (so the transaction can be
 * recorded even when the provider call fails); the timestamp is truncated to milliseconds to match
 * the precision the API exposes. The description and the idempotency key are optional metadata; a
 * null or blank description is stored as null and any other description is stored stripped.
 */
public record TransactionRequest(
        UUID id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        String idempotencyKey,
        Instant createdAt) {

    public TransactionRequest {
        Validations.requirePresent(id, "id");
        Validations.requireText(accountId, "accountId");
        Validations.requirePresent(type, "type");
        amount = Validations.requireMoney(amount, "amount");
        Validations.requireText(currency, "currency");
        description = normalizeDescription(description);
        if (idempotencyKey != null) {
            Validations.requireText(idempotencyKey, "idempotencyKey");
        }
        Validations.requirePresent(createdAt, "createdAt");
        createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }
}
