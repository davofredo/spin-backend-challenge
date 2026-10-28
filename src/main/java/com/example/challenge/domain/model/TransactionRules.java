package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.BusinessRuleViolationException.Code;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Business rules a well-formed {@link TransactionRequest} must satisfy before it reaches the
 * provider: the amount must be strictly greater than 1.00, DEBIT is capped at 10,000.00 and MXN is
 * the only supported currency.
 * <p>
 * Structural correctness (non-null type and amount, non-blank currency) is guaranteed by
 * {@link TransactionRequest}, so this component reports only business rule violations, through
 * {@link BusinessRuleViolationException}.
 */
public final class TransactionRules {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal DEBIT_LIMIT = new BigDecimal("10000.00");
    private static final String SUPPORTED_CURRENCY = "MXN";

    public void validate(TransactionRequest request) {
        Objects.requireNonNull(request, "request");
        validateMinimumAmount(request.amount());
        validateDebitLimit(request.type(), request.amount());
        validateCurrency(request.currency());
    }

    private static void validateMinimumAmount(BigDecimal amount) {
        if (amount.compareTo(MINIMUM_AMOUNT) <= 0) {
            throw new BusinessRuleViolationException(Code.AMOUNT_TOO_LOW,
                    "amount must be greater than " + MINIMUM_AMOUNT.toPlainString()
                            + ", but was " + amount.toPlainString());
        }
    }

    private static void validateDebitLimit(TransactionType type, BigDecimal amount) {
        if (type == TransactionType.DEBIT && amount.compareTo(DEBIT_LIMIT) > 0) {
            throw new BusinessRuleViolationException(Code.DEBIT_LIMIT_EXCEEDED,
                    "DEBIT amount must not exceed " + DEBIT_LIMIT.toPlainString()
                            + ", but was " + amount.toPlainString());
        }
    }

    private static void validateCurrency(String currency) {
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw new BusinessRuleViolationException(Code.UNSUPPORTED_CURRENCY,
                    "currency must be " + SUPPORTED_CURRENCY + ", but was " + currency);
        }
    }
}
