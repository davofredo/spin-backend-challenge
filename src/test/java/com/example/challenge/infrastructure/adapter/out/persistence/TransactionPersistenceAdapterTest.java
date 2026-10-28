package com.example.challenge.infrastructure.adapter.out.persistence;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.out.DuplicateIdempotencyKeyException;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migration against PostgreSQL and exercises the persistence adapter through
 * its port, so Hibernate validation and the SQL see the same schema as production.
 */
@Testcontainers
@SpringBootTest
class TransactionPersistenceAdapterTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant NOW = Instant.parse("2025-01-15T10:00:00Z");

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TransactionJpaRepository springDataRepository;

    @BeforeEach
    void deleteExistingTransactions() {
        springDataRepository.deleteAll();
    }

    @Test
    void shouldReadBackEveryField_whenExecutedTransactionIsSaved() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), "acc-123456", TransactionType.DEBIT, new BigDecimal("250.50"),
                "MXN", "Pago de servicio", "key-1", NOW);
        Transaction transaction = Transaction.executed(request, "prov-777", new BigDecimal("1749.50"));

        repository.save(transaction);
        Transaction stored = repository.findById(request.id()).orElseThrow();

        assertThat(stored.id()).isEqualTo(request.id());
        assertThat(stored.accountId()).isEqualTo("acc-123456");
        assertThat(stored.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(stored.amount()).isEqualByComparingTo("250.50");
        assertThat(stored.amount().scale()).isEqualTo(2);
        assertThat(stored.currency()).isEqualTo("MXN");
        assertThat(stored.description()).isEqualTo("Pago de servicio");
        assertThat(stored.idempotencyKey()).isEqualTo("key-1");
        assertThat(stored.status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(stored.providerTransactionId()).isEqualTo("prov-777");
        assertThat(stored.balanceAfter()).isEqualByComparingTo("1749.50");
        assertThat(stored.balanceAfter().scale()).isEqualTo(2);
        assertThat(stored.errorCode()).isNull();
        assertThat(stored.errorMessage()).isNull();
        assertThat(stored.createdAt()).isEqualTo(NOW);
    }

    @Test
    void shouldReadBackProviderCodeAndMessage_whenRejectedTransactionIsSaved() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), "acc-123456", TransactionType.DEBIT, new BigDecimal("500.00"),
                "MXN", null, null, NOW);
        Transaction transaction = Transaction.rejected(request, "INSUFFICIENT_FUNDS", "Not enough balance");

        repository.save(transaction);
        Transaction stored = repository.findById(request.id()).orElseThrow();

        assertThat(stored.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(stored.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(stored.errorMessage()).isEqualTo("Not enough balance");
        assertThat(stored.description()).isNull();
        assertThat(stored.idempotencyKey()).isNull();
        assertThat(stored.providerTransactionId()).isNull();
        assertThat(stored.balanceAfter()).isNull();
    }

    @Test
    void shouldReadBackProviderUnavailableError_whenFailedTransactionIsSaved() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), "acc-123456", TransactionType.CREDIT, new BigDecimal("120.00"),
                "MXN", null, null, NOW);

        repository.save(Transaction.failed(request));
        Transaction stored = repository.findById(request.id()).orElseThrow();

        assertThat(stored.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(stored.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(stored.errorMessage()).isEqualTo("The provider did not confirm the transaction outcome");
    }

    @Test
    void shouldReturnEmpty_whenTransactionIdIsUnknown() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldFindTransaction_whenIdempotencyKeyWasStored() {
        Transaction transaction = executed("acc-123456", "key-1", NOW);
        repository.save(transaction);

        assertThat(repository.findByIdempotencyKey("key-1"))
                .get()
                .extracting(Transaction::id)
                .isEqualTo(transaction.id());
    }

    @Test
    void shouldReturnEmpty_whenIdempotencyKeyIsUnknown() {
        repository.save(executed("acc-123456", "key-1", NOW));

        assertThat(repository.findByIdempotencyKey("key-2")).isEmpty();
    }

    @Test
    void shouldFilterByAccountId_whenOnlyAccountIdIsGiven() {
        repository.save(executed("acc-1", NOW));
        repository.save(debit("acc-1", NOW.plusSeconds(1)));
        repository.save(executed("acc-2", NOW.plusSeconds(2)));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria("acc-1", null, null, 0, 20));

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).allMatch(transaction -> transaction.accountId().equals("acc-1"));
        assertThat(page.totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldFilterByStatus_whenOnlyStatusIsGiven() {
        repository.save(executed("acc-1", NOW));
        repository.save(rejected("acc-1", NOW.plusSeconds(1)));
        repository.save(failed("acc-1", NOW.plusSeconds(2)));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria(null, TransactionStatus.REJECTED, null, 0, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).status()).isEqualTo(TransactionStatus.REJECTED);
    }

    @Test
    void shouldFilterByType_whenOnlyTypeIsGiven() {
        repository.save(executed("acc-1", NOW));
        repository.save(debit("acc-1", NOW.plusSeconds(1)));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria(null, null, TransactionType.DEBIT, 0, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).type()).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void shouldFilterByEveryCriterion_whenFiltersAreCombined() {
        repository.save(executed("acc-1", NOW));
        repository.save(debit("acc-1", NOW.plusSeconds(1)));
        repository.save(rejected("acc-1", NOW.plusSeconds(2)));
        repository.save(debit("acc-2", NOW.plusSeconds(3)));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria("acc-1", TransactionStatus.EXECUTED, TransactionType.CREDIT, 0, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).accountId()).isEqualTo("acc-1");
        assertThat(page.items().get(0).status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(page.items().get(0).type()).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void shouldReturnEmptyPage_whenNoTransactionMatches() {
        repository.save(executed("acc-1", NOW));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria("acc-unknown", null, null, 0, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldReturnRequestedPageNewestFirst_whenTotalIsLargerThanLimit() {
        for (int second = 1; second <= 5; second++) {
            repository.save(executed("acc-1", NOW.plusSeconds(second)));
        }

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria("acc-1", null, null, 1, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.items())
                .extracting(Transaction::createdAt)
                .containsExactly(NOW.plusSeconds(3), NOW.plusSeconds(2));
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5L);
        assertThat(page.totalPages()).isEqualTo(3L);
    }

    @Test
    void shouldThrowDuplicateIdempotencyKey_whenKeyIsAlreadyStored() {
        Transaction first = executed("acc-1", "key-1", NOW);
        Transaction second = executed("acc-1", "key-1", NOW.plusSeconds(1));
        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOfSatisfying(DuplicateIdempotencyKeyException.class, exception -> {
                    assertThat(exception.idempotencyKey()).isEqualTo("key-1");
                    assertThat(exception).hasMessageContaining("key-1");
                });
    }

    @Test
    void shouldKeepFirstTransaction_whenDuplicateIdempotencyKeyIsRejected() {
        Transaction first = executed("acc-1", "key-1", NOW);
        repository.save(first);

        assertThatThrownBy(() -> repository.save(executed("acc-1", "key-1", NOW.plusSeconds(1))))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);

        assertThat(repository.findByIdempotencyKey("key-1"))
                .get()
                .extracting(Transaction::id)
                .isEqualTo(first.id());
    }

    @Test
    void shouldStoreBothTransactions_whenIdempotencyKeyIsNull() {
        repository.save(executed("acc-1", null, NOW));
        repository.save(executed("acc-1", null, NOW.plusSeconds(1)));

        PageResult<Transaction> page = repository.search(
                new TransactionSearchCriteria("acc-1", null, null, 0, 20));

        assertThat(page.items()).hasSize(2);
    }

    @Test
    void shouldKeepDataIntegrityViolation_whenAnotherConstraintIsViolated() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), "acc-1", TransactionType.CREDIT, new BigDecimal("1000.00"),
                "MXN", null, "key-1", NOW);
        repository.save(Transaction.executed(request, "prov-1", new BigDecimal("1000.00")));

        Transaction duplicateId = Transaction.executed(
                new TransactionRequest(request.id(), "acc-1", TransactionType.CREDIT, new BigDecimal("1000.00"),
                        "MXN", null, "key-2", NOW.plusSeconds(1)),
                "prov-2", new BigDecimal("1000.00"));

        assertThatThrownBy(() -> repository.save(duplicateId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    private static Transaction executed(String accountId, Instant createdAt) {
        return executed(accountId, null, createdAt);
    }

    private static Transaction executed(String accountId, String idempotencyKey, Instant createdAt) {
        return Transaction.executed(
                request(accountId, TransactionType.CREDIT, idempotencyKey, createdAt),
                "prov-1", new BigDecimal("1000.00"));
    }

    private static Transaction debit(String accountId, Instant createdAt) {
        return Transaction.executed(
                request(accountId, TransactionType.DEBIT, null, createdAt),
                "prov-2", new BigDecimal("500.00"));
    }

    private static Transaction rejected(String accountId, Instant createdAt) {
        return Transaction.rejected(
                request(accountId, TransactionType.DEBIT, null, createdAt),
                "INSUFFICIENT_FUNDS", "Not enough balance");
    }

    private static Transaction failed(String accountId, Instant createdAt) {
        return Transaction.failed(request(accountId, TransactionType.CREDIT, null, createdAt));
    }

    private static TransactionRequest request(
            String accountId, TransactionType type, String idempotencyKey, Instant createdAt) {
        return new TransactionRequest(
                UUID.randomUUID(), accountId, type, new BigDecimal("1000.00"),
                "MXN", "Pago de servicio", idempotencyKey, createdAt);
    }
}
