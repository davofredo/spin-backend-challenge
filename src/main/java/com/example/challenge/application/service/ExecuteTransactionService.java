package com.example.challenge.application.service;

import com.example.challenge.application.port.in.ExecuteTransactionCommand;
import com.example.challenge.application.port.in.ExecuteTransactionUseCase;
import com.example.challenge.application.port.out.DuplicateIdempotencyKeyException;
import com.example.challenge.application.port.out.ProviderResult;
import com.example.challenge.application.port.out.TransactionProvider;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link ExecuteTransactionUseCase}: assigns the identity and the timestamp, enforces
 * idempotency and the business rules, asks the provider, and records whatever the provider
 * answered.
 * <p>
 * There is no transaction manager here on purpose: the provider call is remote and must never
 * happen inside a database transaction, so the persistence adapter owns its own short transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteTransactionService implements ExecuteTransactionUseCase {

    /** Stateless domain policy, so it needs no Spring wiring of its own. */
    private static final TransactionRules RULES = new TransactionRules();

    private final TransactionProvider provider;
    private final TransactionRepository repository;
    private final Clock clock;

    @Override
    public Transaction execute(ExecuteTransactionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TransactionRequest request = toRequest(command);

        Optional<Transaction> alreadyExecuted = findByIdempotencyKey(request);
        if (alreadyExecuted.isPresent()) {
            return alreadyExecuted.get();
        }

        RULES.validate(request);

        Transaction transaction = toTransaction(request, provider.execute(request));
        return save(transaction);
    }

    private TransactionRequest toRequest(ExecuteTransactionCommand command) {
        return new TransactionRequest(
                UUID.randomUUID(),
                command.accountId(),
                command.type(),
                command.amount(),
                command.currency(),
                command.description(),
                command.idempotencyKey(),
                clock.instant());
    }

    private Optional<Transaction> findByIdempotencyKey(TransactionRequest request) {
        if (request.idempotencyKey() == null) {
            return Optional.empty();
        }
        Optional<Transaction> stored = repository.findByIdempotencyKey(request.idempotencyKey());
        stored.ifPresent(transaction -> log.info(
                "Idempotency key already used, returning the stored transaction {}", transaction.id()));
        return stored;
    }

    /**
     * A provider answer we cannot interpret is recorded as {@link
     * com.example.challenge.domain.model.TransactionStatus#FAILED}: the outcome is unknown, not
     * absent, and has to remain queryable for reconciliation.
     */
    private static Transaction toTransaction(TransactionRequest request, ProviderResult result) {
        return switch (result) {
            case ProviderResult.Approved approved -> Transaction.executed(
                    request, approved.providerTransactionId(), approved.balance());
            case ProviderResult.Rejected rejected -> Transaction.rejected(
                    request, rejected.code(), rejected.message());
            case ProviderResult.Unavailable ignored -> Transaction.failed(request);
        };
    }

    /**
     * The unique index on the idempotency key can still win a race that the earlier lookup missed,
     * so a duplicate key means another request already stored the transaction.
     */
    private Transaction save(Transaction transaction) {
        try {
            repository.save(transaction);
            return transaction;
        } catch (DuplicateIdempotencyKeyException exception) {
            log.warn("Idempotency key collided while saving transaction {}, re-reading the stored one",
                    transaction.id());
            return repository.findByIdempotencyKey(exception.idempotencyKey())
                    .orElseThrow(() -> exception);
        }
    }
}
