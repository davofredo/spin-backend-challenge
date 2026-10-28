package com.example.challenge.application.model;

import com.example.challenge.domain.exception.InvalidTransactionDataException;
import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionSearchCriteriaTest {

    @Test
    void shouldKeepEveryFilter_whenAllOfThemArePresent() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(
                "acc-123456", TransactionStatus.EXECUTED, TransactionType.DEBIT, 2, 50);

        assertThat(criteria.accountId()).isEqualTo("acc-123456");
        assertThat(criteria.status()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(criteria.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.limit()).isEqualTo(50);
    }

    @Test
    void shouldKeepNullFilters_whenOnlyPaginationIsGiven() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, 0, 20);

        assertThat(criteria.accountId()).isNull();
        assertThat(criteria.status()).isNull();
        assertThat(criteria.type()).isNull();
    }

    @Test
    void shouldAcceptSmallestAllowedPagination_whenPageIsZeroAndLimitIsOne() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(null, null, null, 0, 1);

        assertThat(criteria.page()).isZero();
        assertThat(criteria.limit()).isEqualTo(1);
    }

    @Test
    void shouldNormaliseAccountIdToNull_whenItIsBlank() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria("   ", null, null, 0, 20);

        assertThat(criteria.accountId()).isNull();
    }

    @Test
    void shouldNormaliseAccountIdToNull_whenItIsEmpty() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria("", null, null, 0, 20);

        assertThat(criteria.accountId()).isNull();
    }

    @Test
    void shouldKeepAccountId_whenItHasContent() {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria("acc-123456", null, null, 0, 20);

        assertThat(criteria.accountId()).isEqualTo("acc-123456");
    }

    @Test
    void shouldThrow_whenPageIsNegative() {
        assertThatThrownBy(() -> new TransactionSearchCriteria(null, null, null, -1, 20))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("page must be 0 or greater, but was -1");
    }

    @Test
    void shouldThrow_whenLimitIsZero() {
        assertThatThrownBy(() -> new TransactionSearchCriteria(null, null, null, 0, 0))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("limit must be 1 or greater, but was 0");
    }

    @Test
    void shouldThrow_whenLimitIsNegative() {
        assertThatThrownBy(() -> new TransactionSearchCriteria(null, null, null, 0, -5))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("limit must be 1 or greater, but was -5");
    }
}
