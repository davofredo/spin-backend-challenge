package com.example.challenge.application.service;

import com.example.challenge.application.port.in.ExecuteTransactionCommand;
import com.example.challenge.application.port.out.DuplicateIdempotencyKeyException;
import com.example.challenge.application.port.out.ProviderResult;
import com.example.challenge.application.port.out.TransactionProvider;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.BusinessRuleViolationException.Code;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecuteTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:00:00.123456789Z");
    private static final Instant TRIMMED_NOW = Instant.parse("2025-01-15T10:00:00.123Z");
    private static final Instant PROVIDER_EXECUTED_AT = Instant.parse("2025-01-15T10:00:01Z");
    private static final String ACCOUNT_ID = "acc-123456";
    private static final String DESCRIPTION = "Transferencia recibida";

    @Mock
    private TransactionProvider provider;

    @Mock
    private TransactionRepository repository;

    private ExecuteTransactionService service;

    @BeforeEach
    void setUp() {
        service = new ExecuteTransactionService(provider, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- business rules: the provider is never called and nothing is persisted ---

    @Test
    void shouldRejectRequest_whenAmountIsAtTheMinimum() {
        ExecuteTransactionCommand command = command(new BigDecimal("1.00"), TransactionType.CREDIT, null);

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("amount must be greater than 1.00, but was 1.00")
                .extracting(exception -> ((BusinessRuleViolationException) exception).code())
                .isEqualTo(Code.AMOUNT_TOO_LOW);

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectRequest_whenDebitAmountExceedsTheLimit() {
        ExecuteTransactionCommand command = command(new BigDecimal("10000.01"), TransactionType.DEBIT, null);

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("DEBIT amount must not exceed 10000.00, but was 10000.01")
                .extracting(exception -> ((BusinessRuleViolationException) exception).code())
                .isEqualTo(Code.DEBIT_LIMIT_EXCEEDED);

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectRequest_whenCurrencyIsNotMxn() {
        ExecuteTransactionCommand command = commandWithCurrency(new BigDecimal("1500.00"), "USD");

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("currency must be MXN, but was USD")
                .extracting(exception -> ((BusinessRuleViolationException) exception).code())
                .isEqualTo(Code.UNSUPPORTED_CURRENCY);

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    // --- provider outcomes ---

    @Test
    void shouldPersistExecutedTransaction_whenProviderApproves() {
        when(provider.execute(any())).thenReturn(approved());

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, null));

        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(result.providerTransactionId()).isEqualTo("prov-777");
        assertThat(result.balanceAfter()).isEqualByComparingTo("3000.00");
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.type()).isEqualTo(TransactionType.CREDIT);
        assertThat(result.amount()).isEqualByComparingTo("1500.00");
        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.currency()).isEqualTo("MXN");
        assertThat(result.description()).isEqualTo(DESCRIPTION);
        assertThat(result.idempotencyKey()).isNull();
        assertThat(result.createdAt()).isEqualTo(TRIMMED_NOW);
        assertThat(captureSavedTransaction()).isSameAs(result);
    }

    @Test
    void shouldSendIdentityAndTimestamp_whenCallingTheProvider() {
        when(provider.execute(any())).thenReturn(approved());

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, "key-9"));

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(provider).execute(captor.capture());
        TransactionRequest sent = captor.getValue();

        assertThat(sent.id()).isEqualTo(result.id());
        assertThat(sent.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(sent.type()).isEqualTo(TransactionType.CREDIT);
        assertThat(sent.amount()).isEqualByComparingTo("1500.00");
        assertThat(sent.currency()).isEqualTo("MXN");
        assertThat(sent.description()).isEqualTo(DESCRIPTION);
        assertThat(sent.idempotencyKey()).isEqualTo("key-9");
        assertThat(sent.createdAt()).isEqualTo(TRIMMED_NOW);
    }

    @Test
    void shouldPersistRejectedTransaction_whenProviderRejects() {
        when(provider.execute(any())).thenReturn(new ProviderResult.Rejected(
                "INSUFFICIENT_FUNDS", "The account has not enough funds"));

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.DEBIT, null));

        assertThat(result.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.errorMessage()).isEqualTo("The account has not enough funds");
        assertThat(result.providerTransactionId()).isNull();
        assertThat(result.balanceAfter()).isNull();
        assertThat(captureSavedTransaction()).isSameAs(result);
    }

    @Test
    void shouldPersistFailedTransaction_whenProviderIsUnavailable() {
        when(provider.execute(any())).thenReturn(ProviderResult.unavailable("Provider returned HTTP 503"));

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, null));

        assertThat(result.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.errorMessage())
                .isEqualTo("The provider did not confirm the transaction outcome");
        assertThat(result.providerTransactionId()).isNull();
        assertThat(result.balanceAfter()).isNull();
        assertThat(captureSavedTransaction()).isSameAs(result);
    }

    // --- idempotency ---

    @Test
    void shouldReturnStoredTransaction_whenIdempotencyKeyWasAlreadyUsed() {
        Transaction stored = executedTransaction("key-1");
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored));

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, "key-1"));

        assertThat(result).isSameAs(stored);
        assertThat(result.providerTransactionId()).isEqualTo("prov-existing");
        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotLookUpIdempotencyKey_whenCommandHasNone() {
        when(provider.execute(any())).thenReturn(approved());

        service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, null));

        verify(repository, never()).findByIdempotencyKey(any());
        verify(repository).save(any());
    }

    @Test
    void shouldReturnStoredTransaction_whenSaveHitsDuplicateKey() {
        Transaction stored = executedTransaction("key-1");
        when(repository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored));
        when(provider.execute(any())).thenReturn(approved());
        doThrow(new DuplicateIdempotencyKeyException("key-1", new IllegalStateException("duplicate key")))
                .when(repository).save(any());

        Transaction result = service.execute(command(new BigDecimal("1500.00"), TransactionType.CREDIT, "key-1"));

        assertThat(result).isSameAs(stored);
        assertThat(result.id()).isEqualTo(stored.id());
        verify(provider).execute(any());
        verify(repository).save(any());
    }

    @Test
    void shouldRethrowDuplicateKeyException_whenStoredTransactionCannotBeFound() {
        DuplicateIdempotencyKeyException failure =
                new DuplicateIdempotencyKeyException("key-1", new IllegalStateException("duplicate key"));
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(provider.execute(any())).thenReturn(approved());
        doThrow(failure).when(repository).save(any());

        assertThatThrownBy(() -> service.execute(
                command(new BigDecimal("1500.00"), TransactionType.CREDIT, "key-1")))
                .isSameAs(failure)
                .hasMessage("transaction with idempotency key 'key-1' already exists");
    }

    private Transaction captureSavedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static ProviderResult approved() {
        return new ProviderResult.Approved("prov-777", new BigDecimal("3000.00"), PROVIDER_EXECUTED_AT);
    }

    private static ExecuteTransactionCommand command(BigDecimal amount, TransactionType type, String idempotencyKey) {
        return new ExecuteTransactionCommand(ACCOUNT_ID, type, amount, "MXN", DESCRIPTION, idempotencyKey);
    }

    private static ExecuteTransactionCommand commandWithCurrency(BigDecimal amount, String currency) {
        return new ExecuteTransactionCommand(ACCOUNT_ID, TransactionType.CREDIT, amount, currency, DESCRIPTION, null);
    }

    private static Transaction executedTransaction(String idempotencyKey) {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), ACCOUNT_ID, TransactionType.CREDIT, new BigDecimal("2000.00"),
                "MXN", DESCRIPTION, idempotencyKey, TRIMMED_NOW);
        return Transaction.executed(request, "prov-existing", new BigDecimal("3500.00"));
    }
}
