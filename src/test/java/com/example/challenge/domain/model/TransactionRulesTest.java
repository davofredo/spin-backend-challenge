package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.BusinessRuleViolationException.Code;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.challenge.domain.model.TransactionType.CREDIT;
import static com.example.challenge.domain.model.TransactionType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRulesTest {

    private static final UUID ID = UUID.fromString("7b9a4a2e-6f2b-4c5f-9b1c-2d3e4f5a6b7c");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:15:30Z");

    private final TransactionRules rules = new TransactionRules();

    @Test
    void shouldRejectAmount_whenAmountIsExactlyMinimumBoundary() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "1.00", "MXN")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldRejectAmount_whenAmountIsJustBelowMinimumBoundary() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "0.99", "MXN")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldAcceptAmount_whenAmountIsJustAboveMinimumBoundary() {
        assertThatCode(() -> rules.validate(request(CREDIT, "1.01", "MXN")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAmount_whenAmountIsZero() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "0.00", "MXN")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldRejectAmount_whenAmountIsNegative() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "-50.00", "MXN")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldAcceptDebit_whenAmountIsExactlyDebitLimit() {
        assertThatCode(() -> rules.validate(request(DEBIT, "10000.00", "MXN")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDebit_whenAmountIsAboveDebitLimit() {
        assertThatThrownBy(() -> rules.validate(request(DEBIT, "10000.01", "MXN")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(Code.DEBIT_LIMIT_EXCEEDED);
                    assertThat(ex.getMessage()).contains("10000.00").contains("10000.01");
                });
    }

    @Test
    void shouldAcceptCredit_whenAmountIsAboveDebitLimit() {
        assertThatCode(() -> rules.validate(request(CREDIT, "10000.01", "MXN")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptCredit_whenAmountIsVeryLarge() {
        assertThatCode(() -> rules.validate(request(CREDIT, "999999999999999.99", "MXN")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsNotMxn() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "150.00", "USD")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(Code.UNSUPPORTED_CURRENCY);
                    assertThat(ex.getMessage()).contains("USD");
                });
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsLowercase() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "150.00", "mxn")))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(Code.UNSUPPORTED_CURRENCY);
                    assertThat(ex.getMessage()).contains("mxn");
                });
    }

    @Test
    void shouldDescribeViolation_whenAmountIsTooLow() {
        assertThatThrownBy(() -> rules.validate(request(CREDIT, "1.00", "MXN")))
                .hasMessage("amount must be greater than 1.00, but was 1.00");
    }

    @Test
    void shouldRejectRequest_whenRequestIsNull() {
        assertThatThrownBy(() -> rules.validate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request");
    }

    private static TransactionRequest request(TransactionType type, String amount, String currency) {
        return new TransactionRequest(
                ID, "acc-123456", type, new BigDecimal(amount), currency, "Pago de servicio", null, CREATED_AT);
    }
}
