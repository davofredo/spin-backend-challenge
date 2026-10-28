package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Structurally validates domain data and normalizes monetary values.
 */
final class Validations {

    static final int MONEY_SCALE = 2;

    private Validations() {
    }

    static void requirePresent(Object value, String field) {
        if (value == null) {
            throw new InvalidTransactionDataException(field + " must not be null");
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionDataException(field + " must not be blank");
        }
    }

    static BigDecimal requireMoney(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidTransactionDataException(field + " must not be null");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidTransactionDataException(field + " must have at most " + MONEY_SCALE + " decimals", ex);
        }
    }
}
