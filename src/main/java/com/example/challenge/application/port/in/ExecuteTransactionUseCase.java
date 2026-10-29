package com.example.challenge.application.port.in;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.InvalidTransactionDataException;
import com.example.challenge.domain.model.Transaction;

/**
 * Executes a transaction against the provider and records the outcome.
 */
public interface ExecuteTransactionUseCase {

    /**
     * Validates the command, asks the provider to execute it and stores the resulting transaction.
     * <p>
     * When the command carries an idempotency key that was already used, the stored transaction is
     * returned and the provider is not called again.
     *
     * @param command the transaction to execute
     * @return the stored transaction; an unknown provider outcome is stored as a failed transaction
     * rather than reported as an error, because the outcome must stay visible for reconciliation
     * @throws InvalidTransactionDataException  when the command is not structurally valid
     * @throws BusinessRuleViolationException   when the command breaks a business rule; nothing is
     *                                          sent to the provider and nothing is persisted
     */
    Transaction execute(ExecuteTransactionCommand command);
}
