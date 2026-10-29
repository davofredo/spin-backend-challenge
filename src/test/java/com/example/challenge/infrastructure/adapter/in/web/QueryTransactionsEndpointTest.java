package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.application.model.PageResult;
import com.example.challenge.application.model.TransactionSearchCriteria;
import com.example.challenge.application.port.in.ExecuteTransactionUseCase;
import com.example.challenge.application.port.in.QueryTransactionsUseCase;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class QueryTransactionsEndpointTest {

    private static final UUID TRANSACTION_ID = UUID.fromString("0b6f9d1e-2f3a-4c5b-8d7e-9f0a1b2c3d4e");
    private static final Instant CREATED_AT = Instant.parse("2024-05-01T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecuteTransactionUseCase executeTransaction;

    @MockitoBean
    private QueryTransactionsUseCase queryTransactions;

    @Test
    void shouldReturnFirstPageWithDefaults_whenNoFiltersAreProvided() throws Exception {
        when(queryTransactions.search(any())).thenReturn(new PageResult<>(
                List.of(executedTransaction()), 0, 20, 42));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("EXECUTED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.totalPages").value(3));

        assertThat(capturedCriteria()).isEqualTo(new TransactionSearchCriteria(null, null, null, 0, 20));
    }

    @Test
    void shouldPassEveryFilterToUseCase_whenFiltersAreProvided() throws Exception {
        when(queryTransactions.search(any())).thenReturn(new PageResult<>(List.of(), 2, 10, 0));

        mockMvc.perform(get("/transactions")
                        .param("accountId", "acc-123456")
                        .param("status", "REJECTED")
                        .param("type", "DEBIT")
                        .param("page", "2")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        assertThat(capturedCriteria()).isEqualTo(new TransactionSearchCriteria(
                "acc-123456", TransactionStatus.REJECTED, TransactionType.DEBIT, 2, 10));
    }

    @Test
    void shouldReturnEmptyItemsAndZeroPages_whenNoTransactionMatches() throws Exception {
        when(queryTransactions.search(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/transactions").param("accountId", "acc-unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void shouldTreatAccountIdAsAbsent_whenItIsEmpty() throws Exception {
        when(queryTransactions.search(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/transactions").param("accountId", ""))
                .andExpect(status().isOk());

        assertThat(capturedCriteria().accountId()).isNull();
    }

    @Test
    void shouldTreatAccountIdAsAbsent_whenItIsOnlyWhitespace() throws Exception {
        when(queryTransactions.search(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/transactions").param("accountId", "  "))
                .andExpect(status().isOk());

        assertThat(capturedCriteria().accountId()).isNull();
    }

    @Test
    void shouldReturnBadRequest_whenLimitExceedsMaximum() throws Exception {
        mockMvc.perform(get("/transactions").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid"));

        verifyNoInteractions(queryTransactions);
    }

    @Test
    void shouldReturnBadRequest_whenLimitIsZero() throws Exception {
        mockMvc.perform(get("/transactions").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid"));

        verifyNoInteractions(queryTransactions);
    }

    @Test
    void shouldReturnBadRequest_whenPageIsNegative() throws Exception {
        mockMvc.perform(get("/transactions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(queryTransactions);
    }

    @Test
    void shouldReturnBadRequest_whenStatusIsUnknown() throws Exception {
        mockMvc.perform(get("/transactions").param("status", "SETTLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Parameter 'status' has an invalid value"));

        verifyNoInteractions(queryTransactions);
    }

    @Test
    void shouldReturnBadRequest_whenTypeIsUnknown() throws Exception {
        mockMvc.perform(get("/transactions").param("type", "TRANSFER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Parameter 'type' has an invalid value"));

        verifyNoInteractions(queryTransactions);
    }

    private TransactionSearchCriteria capturedCriteria() {
        ArgumentCaptor<TransactionSearchCriteria> captor = ArgumentCaptor.forClass(TransactionSearchCriteria.class);
        verify(queryTransactions).search(captor.capture());
        return captor.getValue();
    }

    private static Transaction executedTransaction() {
        return Transaction.executed(request(), "prov-777", new BigDecimal("2500.00"));
    }

    private static TransactionRequest request() {
        return new TransactionRequest(
                TRANSACTION_ID, "acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"),
                "MXN", "Transferencia recibida", null, CREATED_AT);
    }
}
