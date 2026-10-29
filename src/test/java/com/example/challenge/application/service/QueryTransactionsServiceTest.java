package com.example.challenge.application.service;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.out.TransactionRepository;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryTransactionsServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-15T10:00:00.123Z");

    @Mock
    private TransactionRepository repository;

    private QueryTransactionsService service;

    @BeforeEach
    void setUp() {
        service = new QueryTransactionsService(repository);
    }

    @Test
    void shouldReturnThePageFromTheRepository_whenSearching() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(
                "acc-123456", TransactionStatus.EXECUTED, TransactionType.CREDIT, 1, 2);
        Transaction first = executedTransaction();
        Transaction second = executedTransaction();
        PageResult<Transaction> page = new PageResult<>(List.of(first, second), 1, 2, 7);
        when(repository.search(criteria)).thenReturn(page);

        PageResult<Transaction> result = service.search(criteria);

        assertThat(result).isSameAs(page);
        assertThat(result.items()).containsExactly(first, second);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.totalPages()).isEqualTo(4);
        verify(repository).search(criteria);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReturnTheEmptyPage_whenNothingMatches() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, 0, 20);
        PageResult<Transaction> page = new PageResult<>(List.of(), 0, 20, 0);
        when(repository.search(criteria)).thenReturn(page);

        PageResult<Transaction> result = service.search(criteria);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void shouldRejectSearch_whenCriteriaIsNull() {
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("criteria must not be null");
    }

    private static Transaction executedTransaction() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), "acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"),
                "MXN", "Transferencia recibida", null, NOW);
        return Transaction.executed(request, "prov-" + request.id(), new BigDecimal("3000.00"));
    }
}
