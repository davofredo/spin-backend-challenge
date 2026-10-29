package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.domain.model.Transaction;

import java.util.List;

/**
 * One page of transactions plus the metadata a client needs to request the next one.
 */
public record TransactionPageResponse(
        List<TransactionResponse> items,
        int page,
        int limit,
        long totalElements,
        long totalPages) {

    public static TransactionPageResponse from(PageResult<Transaction> page) {
        return new TransactionPageResponse(
                page.items().stream().map(TransactionResponse::from).toList(),
                page.page(),
                page.limit(),
                page.totalElements(),
                page.totalPages());
    }
}
