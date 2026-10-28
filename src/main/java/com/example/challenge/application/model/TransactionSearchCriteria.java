package com.example.challenge.application.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;

/**
 * Filters and pagination of a transaction search.
 * <p>
 * Every filter is optional and they combine with AND. {@code page} is zero-based and {@code limit}
 * is the page size; the upper bound of {@code limit} belongs to the web layer, not to this model.
 */
public record TransactionSearchCriteria(
        String accountId,
        TransactionStatus status,
        TransactionType type,
        int page,
        int limit) {

    public TransactionSearchCriteria {
        if (page < 0) {
            throw new InvalidTransactionDataException("page must be 0 or greater, but was " + page);
        }
        if (limit < 1) {
            throw new InvalidTransactionDataException("limit must be 1 or greater, but was " + limit);
        }
    }
}
