package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.BusinessRuleViolationException.Code;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.challenge.domain.model.TransactionType.CREDIT;
import static com.example.challenge.domain.model.TransactionType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRulesTest {

    private final TransactionRules rules = new TransactionRules();

    @Test
    void shouldRejectAmount_whenAmountIsExactlyMinimumBoundary() {
        BigDecimal amount = new BigDecimal("1.00");

        assertThatThrownBy(() -> rules.validate(CREDIT, amount, "MXN"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldAcceptAmount_whenAmountIsJustAboveMinimumBoundary() {
        assertThatCode(() -> rules.validate(CREDIT, new BigDecimal("1.01"), "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAmount_whenAmountIsZero() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("0.00"), "MXN"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldRejectAmount_whenAmountIsNegative() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("-50.00"), "MXN"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.AMOUNT_TOO_LOW));
    }

    @Test
    void shouldRejectAmount_whenAmountIsNull() {
        assertThatThrownBy(() -> rules.validate(CREDIT, null, "MXN"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectType_whenTypeIsNull() {
        assertThatThrownBy(() -> rules.validate(null, new BigDecimal("150.00"), "MXN"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldAcceptDebit_whenAmountIsExactlyDebitLimit() {
        assertThatCode(() -> rules.validate(DEBIT, new BigDecimal("10000.00"), "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDebit_whenAmountIsAboveDebitLimit() {
        assertThatThrownBy(() -> rules.validate(DEBIT, new BigDecimal("10000.01"), "MXN"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.DEBIT_LIMIT_EXCEEDED));
    }

    @Test
    void shouldAcceptCredit_whenAmountIsVeryLarge() {
        assertThatCode(() -> rules.validate(CREDIT, new BigDecimal("999999999999999.99"), "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsNotMxn() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("150.00"), "USD"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.UNSUPPORTED_CURRENCY));
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsNull() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("150.00"), null))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.UNSUPPORTED_CURRENCY));
    }

    @Test
    void shouldRejectCurrency_whenCurrencyIsLowercase() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("150.00"), "mxn"))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        ex -> assertThat(ex.code()).isEqualTo(Code.UNSUPPORTED_CURRENCY));
    }

    @Test
    void shouldDescribeViolation_whenAmountIsTooLow() {
        assertThatThrownBy(() -> rules.validate(CREDIT, new BigDecimal("1.00"), "MXN"))
                .hasMessage("amount must be greater than 1.00, but was 1.00");
    }
}
