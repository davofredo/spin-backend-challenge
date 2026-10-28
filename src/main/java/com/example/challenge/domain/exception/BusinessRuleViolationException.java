package com.example.challenge.domain.exception;

import java.util.Objects;

/**
 * Thrown when a transaction breaks one of the business rules checked before calling the provider.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public enum Code {
        AMOUNT_TOO_LOW,
        DEBIT_LIMIT_EXCEEDED,
        UNSUPPORTED_CURRENCY
    }

    private final Code code;

    public BusinessRuleViolationException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }
}
