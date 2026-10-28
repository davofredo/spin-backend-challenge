package com.example.challenge.application.port.out;

/**
 * Thrown when a transaction is saved with an idempotency key that another transaction already uses.
 * <p>
 * The unique constraint on the key is the last line of defence against executing the same
 * transaction twice, so a caller that hits it must read the existing transaction instead of
 * calling the provider again.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateIdempotencyKeyException(String idempotencyKey, Throwable cause) {
        super("transaction with idempotency key '" + idempotencyKey + "' already exists", cause);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
