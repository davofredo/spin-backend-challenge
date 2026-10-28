package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.BusinessRuleViolationException.Code;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Business rules that must hold before a transaction is sent to the provider.
 * <p>
 * Input is expected to be structurally valid (non-null type and amount, non-blank currency);
 * presence checks belong to the request validation layer.
 */
public final class TransactionRules {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal DEBIT_LIMIT = new BigDecimal("10000.00");
    private static final String SUPPORTED_CURRENCY = "MXN";

    public void validate(TransactionType type, BigDecimal amount, String currency) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        validateMinimumAmount(amount);
        validateDebitLimit(type, amount);
        validateCurrency(currency);
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
