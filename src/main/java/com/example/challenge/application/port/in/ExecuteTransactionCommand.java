package com.example.challenge.application.port.in;

import com.example.challenge.domain.model.TransactionType;

import java.math.BigDecimal;

/**
 * What the caller wants to execute, before the application layer assigns the identity and the
 * timestamp. Structural validation happens in {@code TransactionRequest} and the business rules in
 * {@code TransactionRules}, so this record only carries the intent and stays free of transport and
 * persistence types.
 *
 * @param idempotencyKey optional; when present the same key always resolves to the same transaction
 */
public record ExecuteTransactionCommand(
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        String idempotencyKey) {
}
