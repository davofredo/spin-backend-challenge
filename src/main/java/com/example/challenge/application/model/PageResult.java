package com.example.challenge.application.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of search results together with the metadata needed to request the next one.
 * <p>
 * The pagination values describe what the caller asked for, not client input validated at the edge,
 * so an invalid combination is a programming error: it fails with {@link IllegalArgumentException}.
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
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater, but was " + page);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be 1 or greater, but was " + limit);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be 0 or greater, but was " + totalElements);
        }
        items = List.copyOf(items);
    }

    /**
     * Number of pages needed to hold all elements; zero when the search returned nothing.
     */
    public long totalPages() {
        return (totalElements + limit - 1) / limit;
    }
}
