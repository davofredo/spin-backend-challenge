package com.example.challenge.application.service;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.in.QueryTransactionsUseCase;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Default {@link QueryTransactionsUseCase}: the filters and the pagination are translated by the
 * persistence adapter, so querying is a straight delegation.
 */
@Service
@RequiredArgsConstructor
public class QueryTransactionsService implements QueryTransactionsUseCase {

    private final TransactionRepository repository;

    @Override
    public PageResult<Transaction> search(TransactionSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        return repository.search(criteria);
    }
}
