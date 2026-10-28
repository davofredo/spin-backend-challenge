package com.example.challenge.application.port.out;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.domain.model.Transaction;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for transactions.
 * <p>
 * The implementation lives in the infrastructure layer and maps to and from its own storage types,
 * so the application layer never sees JPA or Spring Data classes.
 */
public interface TransactionRepository {

    /**
     * Stores the transaction.
     *
     * @throws DuplicateIdempotencyKeyException when another transaction already uses the same
     *                                          idempotency key; this is the only expected failure
     */
    void save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Returns the page of transactions matching the criteria, newest first.
     */
    PageResult<Transaction> search(TransactionSearchCriteria criteria);
}
