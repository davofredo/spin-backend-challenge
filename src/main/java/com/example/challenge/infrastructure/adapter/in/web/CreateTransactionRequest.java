package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.domain.model.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code POST /transactions}.
 * <p>
 * Only structural validation lives here; the business rules (minimum amount, DEBIT cap, supported
 * currency) belong to the application layer and are reported as {@code 422}.
 */
public record CreateTransactionRequest(
        @NotBlank @Size(max = 64) String accountId,
        @NotNull TransactionType type,
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 255) String description) {
}
