package com.github.guoyyit.hypermediaresourceapijava.hyper;

import com.github.guoyyit.hypermediaresourceapijava.web.error.BadRequestException;

import java.util.Map;

/**
 * Parsing helpers for stable, 1-based index pagination.
 *
 * Documented query parameters:
 *   page - 1-based page number, default 1; must be {@code >= 1}
 *          and {@code <= hypermedia.pagination.max-page} when that is > 0.
 *   size - items per page, default {@code hypermedia.pagination.default-size};
 *          must be {@code 1..hypermedia.pagination.max-size}.
 */
public final class Pagination {

    private Pagination() {
    }

    public record PageRequest(int page, int size) {
        public long offset() {
            return (long) (page - 1) * size;
        }
    }

    public static PageRequest parsePageRequest(String pageParam, String sizeParam,
                                               int defaultSize, int maxSize, int maxPage) {
        int page = parsePositive(pageParam, 1, "page");
        if (maxPage > 0 && page > maxPage) {
            throw new BadRequestException(
                    "PAGE_OUT_OF_RANGE",
                    "page must be between 1 and " + maxPage,
                    Map.of("self", "/orders", "first", "/orders?page=1"));
        }
        int size = parsePositive(sizeParam, defaultSize, "size");
        if (size > maxSize) {
            throw new BadRequestException(
                    "PAGE_SIZE_TOO_LARGE",
                    "size must not exceed " + maxSize,
                    Map.of("self", "/orders", "first", "/orders?page=1"));
        }
        return new PageRequest(page, size);
    }

    private static int parsePositive(String raw, int fallback, String name) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException(
                    "INVALID_QUERY_PARAMETER",
                    "Query parameter '" + name + "' must be a positive integer",
                    Map.of("self", "/orders", "first", "/orders?page=1"));
        }
        if (value < 1) {
            throw new BadRequestException(
                    "INVALID_QUERY_PARAMETER",
                    "Query parameter '" + name + "' must be a positive integer",
                    Map.of("self", "/orders", "first", "/orders?page=1"));
        }
        return value;
    }
}
