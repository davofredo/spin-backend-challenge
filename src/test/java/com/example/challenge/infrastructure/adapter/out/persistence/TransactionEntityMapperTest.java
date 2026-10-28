package com.example.challenge.infrastructure.adapter.out.persistence;

import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit test of the mapper: no Spring context and no database, so it pins the field-by-field
 * translation without paying for a container.
 */
class TransactionEntityMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00.123Z");

    private final UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void shouldCopyEveryField_whenExecutedTransactionIsMappedToEntity() {
        Transaction transaction = executedTransaction("key-1", "Pago de servicio");

        TransactionJpaEntity entity = TransactionEntityMapper.toEntity(transaction);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getAccountId()).isEqualTo("acc-123456");
        assertThat(entity.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(entity.getAmount()).isEqualByComparingTo("250.50");
        assertThat(entity.getAmount().scale()).isEqualTo(2);
        assertThat(entity.getCurrency()).isEqualTo("MXN");
        assertThat(entity.getDescription()).isEqualTo("Pago de servicio");
        assertThat(entity.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(entity.getProviderTransactionId()).isEqualTo("prov-777");
        assertThat(entity.getBalanceAfter()).isEqualByComparingTo("1749.50");
        assertThat(entity.getBalanceAfter().scale()).isEqualTo(2);
        assertThat(entity.getErrorCode()).isNull();
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldRebuildEveryField_whenExecutedEntityIsMappedToDomain() {
        TransactionJpaEntity entity = executedEntity("key-1", "Pago de servicio");

        Transaction transaction = TransactionEntityMapper.toDomain(entity);

        assertThat(transaction.id()).isEqualTo(id);
        assertThat(transaction.accountId()).isEqualTo("acc-123456");
        assertThat(transaction.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(transaction.amount()).isEqualByComparingTo("250.50");
        assertThat(transaction.amount().scale()).isEqualTo(2);
        assertThat(transaction.currency()).isEqualTo("MXN");
        assertThat(transaction.description()).isEqualTo("Pago de servicio");
        assertThat(transaction.idempotencyKey()).isEqualTo("key-1");
        assertThat(transaction.status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(transaction.providerTransactionId()).isEqualTo("prov-777");
        assertThat(transaction.balanceAfter()).isEqualByComparingTo("1749.50");
        assertThat(transaction.balanceAfter().scale()).isEqualTo(2);
        assertThat(transaction.errorCode()).isNull();
        assertThat(transaction.errorMessage()).isNull();
        assertThat(transaction.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldKeepOptionalFieldsNull_whenExecutedTransactionHasNone() {
        Transaction transaction = executedTransaction(null, null);

        TransactionJpaEntity entity = TransactionEntityMapper.toEntity(transaction);

        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getIdempotencyKey()).isNull();
        assertThat(entity.getErrorCode()).isNull();
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    void shouldKeepOptionalFieldsNull_whenExecutedEntityHasNone() {
        TransactionJpaEntity entity = executedEntity(null, null);

        Transaction transaction = TransactionEntityMapper.toDomain(entity);

        assertThat(transaction.description()).isNull();
        assertThat(transaction.idempotencyKey()).isNull();
        assertThat(transaction.errorCode()).isNull();
        assertThat(transaction.errorMessage()).isNull();
    }

    @Test
    void shouldCopyEveryField_whenRejectedTransactionIsMappedToEntity() {
        Transaction transaction = rejectedTransaction("INSUFFICIENT_FUNDS", "Not enough balance");

        TransactionJpaEntity entity = TransactionEntityMapper.toEntity(transaction);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getAccountId()).isEqualTo("acc-123456");
        assertThat(entity.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(entity.getAmount()).isEqualByComparingTo("250.50");
        assertThat(entity.getCurrency()).isEqualTo("MXN");
        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getIdempotencyKey()).isNull();
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(entity.getProviderTransactionId()).isNull();
        assertThat(entity.getBalanceAfter()).isNull();
        assertThat(entity.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(entity.getErrorMessage()).isEqualTo("Not enough balance");
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldRebuildEveryField_whenRejectedEntityIsMappedToDomain() {
        TransactionJpaEntity entity = new TransactionJpaEntity(
                id, "acc-123456", TransactionType.DEBIT, new BigDecimal("250.50"), "MXN",
                null, null, TransactionStatus.REJECTED, null, null,
                "INSUFFICIENT_FUNDS", "Not enough balance", CREATED_AT);

        Transaction transaction = TransactionEntityMapper.toDomain(entity);

        assertThat(transaction.id()).isEqualTo(id);
        assertThat(transaction.accountId()).isEqualTo("acc-123456");
        assertThat(transaction.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(transaction.amount()).isEqualByComparingTo("250.50");
        assertThat(transaction.currency()).isEqualTo("MXN");
        assertThat(transaction.description()).isNull();
        assertThat(transaction.idempotencyKey()).isNull();
        assertThat(transaction.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(transaction.providerTransactionId()).isNull();
        assertThat(transaction.balanceAfter()).isNull();
        assertThat(transaction.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(transaction.errorMessage()).isEqualTo("Not enough balance");
        assertThat(transaction.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldAllowNullErrorMessage_whenRejectedEntityHasNullMessage() {
        TransactionJpaEntity entity = new TransactionJpaEntity(
                id, "acc-123456", TransactionType.DEBIT, new BigDecimal("250.50"), "MXN",
                null, null, TransactionStatus.REJECTED, null, null,
                "INSUFFICIENT_FUNDS", null, CREATED_AT);

        Transaction transaction = TransactionEntityMapper.toDomain(entity);

        assertThat(transaction.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(transaction.errorMessage()).isNull();
    }

    @Test
    void shouldCopyProviderUnavailableError_whenFailedTransactionIsMappedToEntity() {
        Transaction transaction = failedTransaction();

        TransactionJpaEntity entity = TransactionEntityMapper.toEntity(transaction);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getAccountId()).isEqualTo("acc-123456");
        assertThat(entity.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(entity.getAmount()).isEqualByComparingTo("250.50");
        assertThat(entity.getCurrency()).isEqualTo("MXN");
        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getIdempotencyKey()).isNull();
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(entity.getProviderTransactionId()).isNull();
        assertThat(entity.getBalanceAfter()).isNull();
        assertThat(entity.getErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(entity.getErrorMessage())
                .isEqualTo("The provider did not confirm the transaction outcome");
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldRebuildFailedTransaction_whenFailedEntityIsMappedToDomain() {
        TransactionJpaEntity entity = new TransactionJpaEntity(
                id, "acc-123456", TransactionType.CREDIT, new BigDecimal("250.50"), "MXN",
                null, null, TransactionStatus.FAILED, null, null,
                "PROVIDER_UNAVAILABLE", "The provider did not confirm the transaction outcome", CREATED_AT);

        Transaction transaction = TransactionEntityMapper.toDomain(entity);

        assertThat(transaction.id()).isEqualTo(id);
        assertThat(transaction.accountId()).isEqualTo("acc-123456");
        assertThat(transaction.type()).isEqualTo(TransactionType.CREDIT);
        assertThat(transaction.amount()).isEqualByComparingTo("250.50");
        assertThat(transaction.currency()).isEqualTo("MXN");
        assertThat(transaction.description()).isNull();
        assertThat(transaction.idempotencyKey()).isNull();
        assertThat(transaction.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.providerTransactionId()).isNull();
        assertThat(transaction.balanceAfter()).isNull();
        assertThat(transaction.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(transaction.errorMessage())
                .isEqualTo("The provider did not confirm the transaction outcome");
        assertThat(transaction.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldThrowNullPointerException_whenTransactionIsNull() {
        assertThatThrownBy(() -> TransactionEntityMapper.toEntity(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("transaction");
    }

    @Test
    void shouldThrowNullPointerException_whenEntityIsNull() {
        assertThatThrownBy(() -> TransactionEntityMapper.toDomain(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("entity");
    }

    private Transaction executedTransaction(String idempotencyKey, String description) {
        TransactionRequest request = request(TransactionType.DEBIT, idempotencyKey, description);
        return Transaction.executed(request, "prov-777", new BigDecimal("1749.50"));
    }

    private TransactionJpaEntity executedEntity(String idempotencyKey, String description) {
        return new TransactionJpaEntity(
                id, "acc-123456", TransactionType.DEBIT, new BigDecimal("250.50"), "MXN",
                description, idempotencyKey, TransactionStatus.EXECUTED, "prov-777",
                new BigDecimal("1749.50"), null, null, CREATED_AT);
    }

    private Transaction rejectedTransaction(String errorCode, String errorMessage) {
        return Transaction.rejected(
                request(TransactionType.DEBIT, null, null), errorCode, errorMessage);
    }

    private Transaction failedTransaction() {
        return Transaction.failed(request(TransactionType.CREDIT, null, null));
    }

    private TransactionRequest request(TransactionType type, String idempotencyKey, String description) {
        return new TransactionRequest(
                id, "acc-123456", type, new BigDecimal("250.50"), "MXN", description, idempotencyKey, CREATED_AT);
    }
}
