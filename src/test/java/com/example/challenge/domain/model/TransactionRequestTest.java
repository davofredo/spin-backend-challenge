package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.challenge.domain.model.TransactionType.CREDIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestTest {

    private static final UUID ID = UUID.fromString("7b9a4a2e-6f2b-4c5f-9b1c-2d3e4f5a6b7c");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:15:30Z");

    @Test
    void shouldNormalizeAmountToScaleTwo_whenAmountHasNoDecimals() {
        TransactionRequest request = new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("1500"), "MXN", "Transferencia recibida", null, CREATED_AT);

        assertThat(request.amount()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(request.amount().scale()).isEqualTo(2);
    }

    @Test
    void shouldKeepAmount_whenAmountAlreadyHasTwoDecimals() {
        TransactionRequest request = request(new BigDecimal("1500.75"));

        assertThat(request.amount()).isEqualTo(new BigDecimal("1500.75"));
    }

    @Test
    void shouldRejectAmount_whenAmountHasMoreThanTwoDecimals() {
        assertThatThrownBy(() -> request(new BigDecimal("1500.755")))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectAmount_whenAmountIsNull() {
        assertThatThrownBy(() -> request(null))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("amount must not be null");
    }

    @Test
    void shouldRejectId_whenIdIsNull() {
        assertThatThrownBy(() -> new TransactionRequest(
                null, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectAccountId_whenAccountIdIsNull() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, null, CREDIT, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("accountId must not be blank");
    }

    @Test
    void shouldRejectAccountId_whenAccountIdIsBlank() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "  ", CREDIT, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("accountId must not be blank");
    }

    @Test
    void shouldRejectType_whenTypeIsNull() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "acc-123456", null, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("type must not be null");
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsNull() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), null, null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("currency must not be blank");
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsBlank() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), " ", null, null, CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("currency must not be blank");
    }

    @Test
    void shouldRejectCreatedAt_whenCreatedAtIsNull() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, null, null))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("createdAt must not be null");
    }

    @Test
    void shouldAllowDescription_whenDescriptionIsNull() {
        TransactionRequest request = new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT);

        assertThat(request.description()).isNull();
    }

    @Test
    void shouldAllowIdempotencyKey_whenIdempotencyKeyIsNull() {
        TransactionRequest request = new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, null, CREATED_AT);

        assertThat(request.idempotencyKey()).isNull();
    }

    @Test
    void shouldKeepIdempotencyKey_whenIdempotencyKeyHasContent() {
        TransactionRequest request = new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, "key-1", CREATED_AT);

        assertThat(request.idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void shouldRejectIdempotencyKey_whenIdempotencyKeyIsBlank() {
        assertThatThrownBy(() -> new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, "   ", CREATED_AT))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("idempotencyKey must not be blank");
    }

    @Test
    void shouldTruncateCreatedAtToMilliseconds_whenCreatedAtHasNanosecondPrecision() {
        TransactionRequest request = new TransactionRequest(
                ID, "acc-123456", CREDIT, new BigDecimal("10.00"), "MXN", null, null,
                Instant.parse("2025-01-15T10:15:30.123456789Z"));

        assertThat(request.createdAt()).isEqualTo(Instant.parse("2025-01-15T10:15:30.123Z"));
    }

    private static TransactionRequest request(BigDecimal amount) {
        return new TransactionRequest(
                ID, "acc-123456", CREDIT, amount, "MXN", "Transferencia recibida", null, CREATED_AT);
    }
}
