package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.challenge.domain.model.TransactionStatus.EXECUTED;
import static com.example.challenge.domain.model.TransactionStatus.FAILED;
import static com.example.challenge.domain.model.TransactionStatus.REJECTED;
import static com.example.challenge.domain.model.TransactionType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final UUID ID = UUID.fromString("7b9a4a2e-6f2b-4c5f-9b1c-2d3e4f5a6b7c");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:15:30Z");
    private static final TransactionRequest REQUEST = new TransactionRequest(
            ID, "acc-123456", DEBIT, new BigDecimal("1500.00"), "MXN", "Pago de servicio", "key-1", CREATED_AT);

    @Test
    void shouldBuildExecutedTransaction_whenProviderApproves() {
        Transaction transaction = Transaction.executed(REQUEST, "prov-999", new BigDecimal("8500.00"));

        assertThat(transaction.status()).isEqualTo(EXECUTED);
        assertThat(transaction.providerTransactionId()).isEqualTo("prov-999");
        assertThat(transaction.balanceAfter()).isEqualTo(new BigDecimal("8500.00"));
        assertThat(transaction.errorCode()).isNull();
        assertThat(transaction.errorMessage()).isNull();
    }

    @Test
    void shouldCopyRequestValues_whenTransactionIsBuilt() {
        Transaction transaction = Transaction.executed(REQUEST, "prov-999", new BigDecimal("8500.00"));

        assertThat(transaction.id()).isEqualTo(ID);
        assertThat(transaction.accountId()).isEqualTo("acc-123456");
        assertThat(transaction.type()).isEqualTo(DEBIT);
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(transaction.currency()).isEqualTo("MXN");
        assertThat(transaction.description()).isEqualTo("Pago de servicio");
        assertThat(transaction.idempotencyKey()).isEqualTo("key-1");
        assertThat(transaction.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldNormalizeBalanceAfterToScaleTwo_whenBalanceHasNoDecimals() {
        Transaction transaction = Transaction.executed(REQUEST, "prov-999", new BigDecimal("8500"));

        assertThat(transaction.balanceAfter()).isEqualTo(new BigDecimal("8500.00"));
        assertThat(transaction.balanceAfter().scale()).isEqualTo(2);
    }

    @Test
    void shouldRejectExecutedTransaction_whenBalanceAfterHasMoreThanTwoDecimals() {
        assertThatThrownBy(() -> Transaction.executed(REQUEST, "prov-999", new BigDecimal("8500.005")))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("balanceAfter");
    }

    @Test
    void shouldRejectExecutedTransaction_whenProviderTransactionIdIsBlank() {
        assertThatThrownBy(() -> Transaction.executed(REQUEST, " ", new BigDecimal("8500.00")))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("providerTransactionId");
    }

    @Test
    void shouldRejectExecutedTransaction_whenBalanceAfterIsNull() {
        assertThatThrownBy(() -> Transaction.executed(REQUEST, "prov-999", null))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("balanceAfter");
    }

    @Test
    void shouldBuildRejectedTransaction_whenProviderRejects() {
        Transaction transaction = Transaction.rejected(REQUEST, "INSUFFICIENT_FUNDS", "Not enough balance");

        assertThat(transaction.status()).isEqualTo(REJECTED);
        assertThat(transaction.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(transaction.errorMessage()).isEqualTo("Not enough balance");
        assertThat(transaction.providerTransactionId()).isNull();
        assertThat(transaction.balanceAfter()).isNull();
    }

    @Test
    void shouldRejectRejectedTransaction_whenErrorCodeIsBlank() {
        assertThatThrownBy(() -> Transaction.rejected(REQUEST, null, "Not enough balance"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessageContaining("errorCode");
    }

    @Test
    void shouldBuildFailedTransaction_whenProviderOutcomeIsUnknown() {
        Transaction transaction = Transaction.failed(REQUEST);

        assertThat(transaction.status()).isEqualTo(FAILED);
        assertThat(transaction.providerTransactionId()).isNull();
        assertThat(transaction.balanceAfter()).isNull();
    }

    @Test
    void shouldUseFixedProviderUnavailableError_whenProviderOutcomeIsUnknown() {
        Transaction transaction = Transaction.failed(REQUEST);

        assertThat(transaction.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(transaction.errorMessage()).isEqualTo("The provider did not confirm the transaction outcome");
    }

    @Test
    void shouldRejectTransaction_whenRequestIsNull() {
        assertThatThrownBy(() -> Transaction.failed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    void shouldRejectExecutedTransaction_whenRequestIsNull() {
        assertThatThrownBy(() -> Transaction.executed(null, "prov-999", new BigDecimal("8500.00")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    void shouldRejectRejectedTransaction_whenRequestIsNull() {
        assertThatThrownBy(() -> Transaction.rejected(null, "INSUFFICIENT_FUNDS", "Not enough balance"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }
}
