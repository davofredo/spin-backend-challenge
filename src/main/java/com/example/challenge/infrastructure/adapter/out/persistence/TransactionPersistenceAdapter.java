package com.example.challenge.infrastructure.adapter.out.persistence;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.out.DuplicateIdempotencyKeyException;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter: turns the persistence port into Spring Data JPA calls and keeps every Spring
 * type inside this package.
 * <p>
 * Each operation is a single statement (or a single page query), so no outer transaction is needed.
 */
@Component
@RequiredArgsConstructor
class TransactionPersistenceAdapter implements TransactionRepository {

    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uq_transactions_idempotency_key";

    private final TransactionJpaRepository repository;

    @Override
    public void save(Transaction transaction) {
        try {
            repository.saveAndFlush(TransactionEntityMapper.toEntity(transaction));
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex, transaction.idempotencyKey());
        }
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return repository.findById(id).map(TransactionEntityMapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(TransactionEntityMapper::toDomain);
    }

    @Override
    public PageResult<Transaction> search(TransactionSearchCriteria criteria) {
        PageRequest pageable = PageRequest.of(
                criteria.page(),
                criteria.limit(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<TransactionJpaEntity> page =
                repository.search(criteria.accountId(), criteria.status(), criteria.type(), pageable);
        return new PageResult<>(
                page.getContent().stream().map(TransactionEntityMapper::toDomain).toList(),
                criteria.page(),
                criteria.limit(),
                page.getTotalElements());
    }

    /**
     * Only a violation of the idempotency key constraint is translated; any other integrity problem
     * is a bug and must keep its original type.
     */
    private static RuntimeException translate(DataIntegrityViolationException ex, String idempotencyKey) {
        if (idempotencyKey != null && violatesIdempotencyKey(ex)) {
            return new DuplicateIdempotencyKeyException(idempotencyKey, ex);
        }
        return ex;
    }

    private static boolean violatesIdempotencyKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains(IDEMPOTENCY_KEY_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
