package com.example.challenge.domain.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationsTest {

    @Test
    void shouldRejectValue_whenRequiredValueIsNull() {
        assertThatThrownBy(() -> Validations.requirePresent(null, "field"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("field must not be null");
    }

    @Test
    void shouldAcceptValue_whenRequiredValueIsPresent() {
        assertThatCode(() -> Validations.requirePresent("value", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectText_whenTextIsNull() {
        assertThatThrownBy(() -> Validations.requireText(null, "field"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("field must not be blank");
    }

    @Test
    void shouldRejectText_whenTextIsBlank() {
        assertThatThrownBy(() -> Validations.requireText("   ", "field"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("field must not be blank");
    }

    @Test
    void shouldAcceptText_whenTextHasContent() {
        assertThatCode(() -> Validations.requireText("value", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMoney_whenMoneyIsNull() {
        assertThatThrownBy(() -> Validations.requireMoney(null, "amount"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("amount must not be null");
    }

    @Test
    void shouldReturnMoneyWithScaleTwo_whenMoneyHasNoDecimals() {
        BigDecimal money = Validations.requireMoney(new BigDecimal("1500"), "amount");

        assertThat(money).isEqualTo(new BigDecimal("1500.00"));
        assertThat(money.scale()).isEqualTo(2);
    }

    @Test
    void shouldReturnSameMoney_whenMoneyAlreadyHasScaleTwo() {
        BigDecimal money = Validations.requireMoney(new BigDecimal("1500.75"), "amount");

        assertThat(money).isEqualTo(new BigDecimal("1500.75"));
        assertThat(money.scale()).isEqualTo(2);
    }

    @Test
    void shouldRejectMoney_whenMoneyHasMoreThanTwoDecimals() {
        assertThatThrownBy(() -> Validations.requireMoney(new BigDecimal("1500.755"), "amount"))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("amount must have at most 2 decimals");
    }
}
