package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.application.port.in.ExecuteTransactionCommand;
import com.example.challenge.application.port.in.ExecuteTransactionUseCase;
import com.example.challenge.application.port.in.QueryTransactionsUseCase;
import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.InvalidTransactionDataException;
import com.example.challenge.domain.model.Transaction;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class ExecuteTransactionEndpointTest {

    private static final UUID TRANSACTION_ID = UUID.fromString("0b6f9d1e-2f3a-4c5b-8d7e-9f0a1b2c3d4e");
    private static final Instant CREATED_AT = Instant.parse("2024-05-01T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExecuteTransactionUseCase executeTransaction;

    @MockitoBean
    private QueryTransactionsUseCase queryTransactions;

    @Test
    void shouldReturnCreatedTransaction_whenProviderApproves() throws Exception {
        Transaction executed = Transaction.executed(request(), "prov-777", new BigDecimal("2500.00"));
        when(executeTransaction.execute(any())).thenReturn(executed);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.accountId").value("acc-123456"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.description").value("Transferencia recibida"))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.providerTransactionId").value("prov-777"))
                .andExpect(jsonPath("$.balanceAfter").value(2500.00))
                .andExpect(jsonPath("$.createdAt").value("2024-05-01T10:15:30Z"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void shouldPassRequestFieldsToUseCase_whenRequestIsValid() throws Exception {
        when(executeTransaction.execute(any())).thenReturn(Transaction.executed(
                request(), "prov-777", new BigDecimal("2500.00")));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isCreated());

        ArgumentCaptor<ExecuteTransactionCommand> captor = ArgumentCaptor.forClass(ExecuteTransactionCommand.class);
        verify(executeTransaction).execute(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ExecuteTransactionCommand(
                "acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"),
                "MXN", "Transferencia recibida", null));
    }

    @Test
    void shouldPassIdempotencyKeyToUseCase_whenHeaderIsPresent() throws Exception {
        when(executeTransaction.execute(any())).thenReturn(Transaction.executed(
                request(), "prov-777", new BigDecimal("2500.00")));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isCreated());

        ArgumentCaptor<ExecuteTransactionCommand> captor = ArgumentCaptor.forClass(ExecuteTransactionCommand.class);
        verify(executeTransaction).execute(captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("key-123");
    }

    @Test
    void shouldReturnBadRequest_whenIdempotencyKeyIsTooLong() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid"));

        verifyNoInteractions(executeTransaction);
    }

    @Test
    void shouldReturnRejectedTransaction_whenProviderRejects() throws Exception {
        when(executeTransaction.execute(any())).thenReturn(
                Transaction.rejected(request(), "INSUFFICIENT_FUNDS", "Not enough funds"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.providerTransactionId").doesNotExist())
                .andExpect(jsonPath("$.balanceAfter").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.errorMessage").value("Not enough funds"));
    }

    @Test
    void shouldReturnBadGatewayWithGenericDetail_whenProviderOutcomeIsUnknown() throws Exception {
        when(executeTransaction.execute(any())).thenReturn(Transaction.failed(request()));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("Bad Gateway"))
                .andExpect(jsonPath("$.detail").value(
                        "The provider did not confirm the transaction outcome; "
                                + "the transaction was recorded as failed"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void shouldReturnUnprocessableEntityWithRuleCode_whenAmountIsTooLow() throws Exception {
        when(executeTransaction.execute(any())).thenThrow(new BusinessRuleViolationException(
                BusinessRuleViolationException.Code.AMOUNT_TOO_LOW,
                "amount must be greater than 1.00, but was 1.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("AMOUNT_TOO_LOW"))
                .andExpect(jsonPath("$.detail").value("amount must be greater than 1.00, but was 1.00"));
    }

    @ParameterizedTest(name = "currency {0}")
    @ValueSource(strings = {"USD", "mxn"})
    void shouldReturnUnprocessableEntityWithRuleCode_whenCurrencyIsNotSupported(String currency) throws Exception {
        when(executeTransaction.execute(any())).thenThrow(new BusinessRuleViolationException(
                BusinessRuleViolationException.Code.UNSUPPORTED_CURRENCY,
                "currency must be MXN, but was " + currency));

        Map<String, Object> body = validBody();
        body.put("currency", currency);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"))
                .andExpect(jsonPath("$.detail").value("currency must be MXN, but was " + currency));
    }

    @ParameterizedTest(name = "currency {0}")
    @ValueSource(strings = {"US", "MXNN"})
    void shouldReturnBadRequest_whenCurrencyLengthIsNotThree(String currency) throws Exception {
        Map<String, Object> body = validBody();
        body.put("currency", currency);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid"));

        verifyNoInteractions(executeTransaction);
    }

    @ParameterizedTest(name = "amount {0}")
    @ValueSource(strings = {"1.00", "0", "-5.00"})
    void shouldReturnUnprocessableEntityWithRuleCode_whenAmountIsNotAboveTheMinimum(String amount)
            throws Exception {
        BigDecimal value = new BigDecimal(amount).setScale(2);
        when(executeTransaction.execute(any())).thenThrow(new BusinessRuleViolationException(
                BusinessRuleViolationException.Code.AMOUNT_TOO_LOW,
                "amount must be greater than 1.00, but was " + value.toPlainString()));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bodyWith(value))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("AMOUNT_TOO_LOW"))
                .andExpect(jsonPath("$.detail").value(
                        "amount must be greater than 1.00, but was " + value.toPlainString()));

        verify(executeTransaction).execute(any());
    }

    @Test
    void shouldSerializeLargeAmountAsPlainDecimal_whenCreditIsAccepted() throws Exception {
        BigDecimal hugeAmount = new BigDecimal("100000000000000.00");
        when(executeTransaction.execute(any())).thenReturn(
                Transaction.executed(requestWith(hugeAmount), "prov-huge", hugeAmount));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bodyWith(hugeAmount))))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"amount\":100000000000000.00")))
                .andExpect(content().string(not(containsString("E+"))));
    }

    @Test
    void shouldReturnUnprocessableEntityWithRuleCode_whenDebitLimitIsExceeded() throws Exception {
        when(executeTransaction.execute(any())).thenThrow(new BusinessRuleViolationException(
                BusinessRuleViolationException.Code.DEBIT_LIMIT_EXCEEDED,
                "DEBIT amount must not exceed 10000.00, but was 10000.01"));

        Map<String, Object> body = validBody();
        body.put("type", "DEBIT");
        body.put("amount", new BigDecimal("10000.01"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEBIT_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldReturnBadRequest_whenRequestDataIsInvalid() throws Exception {
        when(executeTransaction.execute(any()))
                .thenThrow(new InvalidTransactionDataException("amount must have scale 2"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("amount must have scale 2"));
    }

    @Test
    void shouldReturnInternalServerErrorWithoutInternalText_whenUseCaseThrowsUnexpectedly() throws Exception {
        when(executeTransaction.execute(any())).thenThrow(new IllegalStateException("database password leaked"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("database password leaked"))));
    }

    @Test
    void shouldReturnBadRequest_whenBodyIsNotReadable() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The request body could not be read"));

        verifyNoInteractions(executeTransaction);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void shouldReturnBadRequest_whenRequestIsInvalid(String scenario, Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid"))
                .andExpect(jsonPath("$.errors").isArray());

        verifyNoInteractions(executeTransaction);
    }

    private static Stream<Arguments> invalidBodies() {
        Map<String, Object> blankAccountId = validBody();
        blankAccountId.put("accountId", "   ");
        Map<String, Object> longAccountId = validBody();
        longAccountId.put("accountId", "a".repeat(65));
        Map<String, Object> missingType = validBody();
        missingType.put("type", null);
        Map<String, Object> missingAmount = validBody();
        missingAmount.put("amount", null);
        Map<String, Object> tooPreciseAmount = validBody();
        tooPreciseAmount.put("amount", new BigDecimal("1500.755"));
        Map<String, Object> blankCurrency = validBody();
        blankCurrency.put("currency", "  ");
        Map<String, Object> longDescription = validBody();
        longDescription.put("description", "d".repeat(256));

        return Stream.of(
                Arguments.of("blank accountId", blankAccountId),
                Arguments.of("accountId longer than 64 characters", longAccountId),
                Arguments.of("missing type", missingType),
                Arguments.of("missing amount", missingAmount),
                Arguments.of("amount with more than two decimals", tooPreciseAmount),
                Arguments.of("blank currency", blankCurrency),
                Arguments.of("description longer than 255 characters", longDescription));
    }

    private static Map<String, Object> validBody() {
        return bodyWith(new BigDecimal("1500.00"));
    }

    private static Map<String, Object> bodyWith(BigDecimal amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", "acc-123456");
        body.put("type", "CREDIT");
        body.put("amount", amount);
        body.put("currency", "MXN");
        body.put("description", "Transferencia recibida");
        return body;
    }

    private static TransactionRequest request() {
        return requestWith(new BigDecimal("1500.00"));
    }

    private static TransactionRequest requestWith(BigDecimal amount) {
        return new TransactionRequest(
                TRANSACTION_ID, "acc-123456", TransactionType.CREDIT, amount,
                "MXN", "Transferencia recibida", null, CREATED_AT);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
