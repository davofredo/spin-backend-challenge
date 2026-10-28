package com.example.challenge.domain.exception;

/**
 * Thrown when a domain object is built from data that breaks its structural invariants,
 * such as a missing identifier or a monetary value with more than two decimals.
 */
public class InvalidTransactionDataException extends RuntimeException {

    public InvalidTransactionDataException(String message) {
        super(message);
    }

    public InvalidTransactionDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
