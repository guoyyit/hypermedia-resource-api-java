package com.github.guoyyit.hypermediaresourceapijava.error;

/**
 * Raised when a paginated request targets a page index beyond the last page.
 * Mapped to HTTP 416 with code {@code page-out-of-range}; distinct from a
 * valid empty page on a genuinely empty collection (HTTP 200).
 */
public class RangeNotSatisfiableException extends RuntimeException {
    public RangeNotSatisfiableException(String message) {
        super(message);
    }
}
