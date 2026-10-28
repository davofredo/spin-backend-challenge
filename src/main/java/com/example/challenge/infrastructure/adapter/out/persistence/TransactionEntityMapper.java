package com.example.challenge.infrastructure.adapter.out.persistence;

import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;

import java.util.Objects;

/**
 * Translates between the domain {@link Transaction} and its relational representation.
 */
final class TransactionEntityMapper {

    private TransactionEntityMapper() {
    }

    static TransactionJpaEntity toEntity(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        return new TransactionJpaEntity(
                transaction.id(),
                transaction.accountId(),
                transaction.type(),
                transaction.amount(),
                transaction.currency(),
                transaction.description(),
                transaction.idempotencyKey(),
                transaction.status(),
                transaction.providerTransactionId(),
                transaction.balanceAfter(),
                transaction.errorCode(),
                transaction.errorMessage(),
                transaction.createdAt());
    }

    /**
     * Rebuilds the domain object through its factories so a stored row cannot bypass the invariants
     * that each status guarantees.
     */
    static Transaction toDomain(TransactionJpaEntity entity) {
        Objects.requireNonNull(entity, "entity");
        TransactionRequest request = new TransactionRequest(
                entity.getId(),
                entity.getAccountId(),
                entity.getType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getDescription(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt());
        return switch (entity.getStatus()) {
            case EXECUTED -> Transaction.executed(
                    request, entity.getProviderTransactionId(), entity.getBalanceAfter());
            case REJECTED -> Transaction.rejected(
                    request, entity.getErrorCode(), entity.getErrorMessage());
            case FAILED -> Transaction.failed(request);
        };
    }
}
