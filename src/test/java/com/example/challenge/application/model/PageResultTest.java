package com.example.challenge.application.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    @Test
    void shouldKeepPaginationMetadata_whenPageIsBuilt() {
        PageResult<String> page = new PageResult<>(List.of("a", "b"), 1, 2, 5L);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5L);
        assertThat(page.items()).containsExactly("a", "b");
    }

    @Test
    void shouldCopyItems_whenSourceListChangesAfterwards() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        PageResult<String> page = new PageResult<>(source, 0, 20, 2L);
        source.add("c");

        assertThat(page.items()).containsExactly("a", "b");
    }

    @Test
    void shouldBeUnmodifiable_whenCallerTriesToChangeItems() {
        PageResult<String> page = new PageResult<>(List.of("a"), 0, 20, 1L);

        assertThatThrownBy(() -> page.items().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldComputeTotalPages_whenTotalIsNotAMultipleOfLimit() {
        PageResult<String> page = new PageResult<>(List.of(), 0, 20, 45L);

        assertThat(page.totalPages()).isEqualTo(3L);
    }

    @Test
    void shouldComputeTotalPages_whenTotalIsAMultipleOfLimit() {
        PageResult<String> page = new PageResult<>(List.of(), 0, 20, 40L);

        assertThat(page.totalPages()).isEqualTo(2L);
    }

    @Test
    void shouldComputeTotalPages_whenThereAreNoElements() {
        PageResult<String> page = new PageResult<>(List.of(), 0, 20, 0L);

        assertThat(page.totalPages()).isZero();
    }

    @Test
    void shouldThrow_whenItemsIsNull() {
        assertThatThrownBy(() -> new PageResult<String>(null, 0, 20, 0L))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items");
    }

    @Test
    void shouldThrow_whenPageIsNegative() {
        assertThatThrownBy(() -> new PageResult<String>(List.of(), -1, 20, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be 0 or greater, but was -1");
    }

    @Test
    void shouldThrow_whenLimitIsZero() {
        assertThatThrownBy(() -> new PageResult<String>(List.of(), 0, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be 1 or greater, but was 0");
    }

    @Test
    void shouldThrow_whenTotalElementsIsNegative() {
        assertThatThrownBy(() -> new PageResult<String>(List.of(), 0, 20, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalElements must be 0 or greater, but was -1");
    }

    @Test
    void shouldBuildPage_whenValuesAreAtTheirLowerBounds() {
        PageResult<String> page = new PageResult<>(List.of(), 0, 1, 0L);

        assertThat(page.page()).isZero();
        assertThat(page.limit()).isEqualTo(1);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }
}
