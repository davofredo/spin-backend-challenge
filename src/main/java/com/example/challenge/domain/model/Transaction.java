package com.example.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recorded outcome of a transaction execution.
 * <p>
 * Instances can only be created through the factory methods, which couple the status with the
 * data that each outcome requires: an {@code EXECUTED} transaction carries the provider
 * identifier and resulting balance, a {@code REJECTED} one carries the provider's error code and
 * message, and a {@code FAILED} one carries the fixed {@link #PROVIDER_UNAVAILABLE_CODE} error
 * because the provider never confirmed the outcome.
 */
public final class Transaction {

    static final String PROVIDER_UNAVAILABLE_CODE = "PROVIDER_UNAVAILABLE";
    static final String PROVIDER_UNAVAILABLE_MESSAGE = "The provider did not confirm the transaction outcome";

    private final UUID id;
    private final String accountId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String currency;
    private final String description;
    private final String idempotencyKey;
    private final TransactionStatus status;
    private final String providerTransactionId;
    private final BigDecimal balanceAfter;
    private final String errorCode;
    private final String errorMessage;
    private final Instant createdAt;

    private Transaction(
            TransactionRequest request,
            TransactionStatus status,
            String providerTransactionId,
            BigDecimal balanceAfter,
            String errorCode,
            String errorMessage) {
        this.id = request.id();
        this.accountId = request.accountId();
        this.type = request.type();
        this.amount = request.amount();
        this.currency = request.currency();
        this.description = request.description();
        this.idempotencyKey = request.idempotencyKey();
        this.createdAt = request.createdAt();
        this.status = status;
        this.providerTransactionId = providerTransactionId;
        this.balanceAfter = balanceAfter;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Builds the transaction from a provider approval.
     */
    public static Transaction executed(
            TransactionRequest request, String providerTransactionId, BigDecimal balanceAfter) {
        Objects.requireNonNull(request, "request");
        Validations.requireText(providerTransactionId, "providerTransactionId");
        return new Transaction(request, TransactionStatus.EXECUTED, providerTransactionId,
                Validations.requireMoney(balanceAfter, "balanceAfter"), null, null);
    }

    /**
     * Builds the transaction from a provider rejection.
     */
    public static Transaction rejected(TransactionRequest request, String errorCode, String errorMessage) {
        Objects.requireNonNull(request, "request");
        Validations.requireText(errorCode, "errorCode");
        return new Transaction(request, TransactionStatus.REJECTED, null, null, errorCode, errorMessage);
    }

    /**
     * Builds the transaction when the provider outcome is unknown (unreachable, timeout or open circuit).
     */
    public static Transaction failed(TransactionRequest request) {
        Objects.requireNonNull(request, "request");
        return new Transaction(request, TransactionStatus.FAILED, null, null,
                PROVIDER_UNAVAILABLE_CODE, PROVIDER_UNAVAILABLE_MESSAGE);
    }

    public UUID id() {
        return id;
    }

    public String accountId() {
        return accountId;
    }

    public TransactionType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String description() {
        return description;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public TransactionStatus status() {
        return status;
    }

    public String providerTransactionId() {
        return providerTransactionId;
    }

    public BigDecimal balanceAfter() {
        return balanceAfter;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
