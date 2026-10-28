package com.example.challenge.application.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of search results together with the metadata needed to request the next one.
 *
 * @param items         the elements of this page, in the order defined by the search
 * @param page          the zero-based index of this page
 * @param limit         the requested page size
 * @param totalElements total number of elements matching the search, ignoring pagination
 * @param <T>           element type
 */
public record PageResult<T>(List<T> items, int page, int limit, long totalElements) {

    public PageResult {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    /**
     * Number of pages needed to hold all elements; zero when the search returned nothing.
     */
    public long totalPages() {
        return (totalElements + limit - 1) / limit;
    }
}
