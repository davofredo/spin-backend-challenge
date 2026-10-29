package com.example.challenge.application.port.in;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.domain.model.Transaction;

/**
 * Queries previously stored transactions.
 */
public interface QueryTransactionsUseCase {

    /**
     * @param criteria combinable filters plus the mandatory pagination
     * @return one page of transactions matching the criteria
     */
    PageResult<Transaction> search(TransactionSearchCriteria criteria);
}
