package com.github.guoyyit.hypermediaresourceapijava.error;

/**
 * Raised when a conditional update is attempted without an If-Match header.
 * Mapped to HTTP 428 with code {@code precondition-required}.
 */
public class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException(String message) {
        super(message);
    }
}
