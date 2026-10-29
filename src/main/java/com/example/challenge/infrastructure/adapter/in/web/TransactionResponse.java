package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representation of a stored transaction.
 * <p>
 * The optional fields are omitted instead of being serialized as {@code null}: a provider
 * identifier only exists when the provider approved the transaction, and the error fields only
 * exist for a {@code REJECTED} or {@code FAILED} outcome.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String providerTransactionId,
        BigDecimal balanceAfter,
        Instant createdAt,
        String errorCode,
        String errorMessage) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.accountId(),
                transaction.type(),
                transaction.amount(),
                transaction.currency(),
                transaction.description(),
                transaction.status(),
                transaction.providerTransactionId(),
                transaction.balanceAfter(),
                transaction.createdAt(),
                transaction.errorCode(),
                transaction.errorMessage());
    }
}
