package com.github.guoyyit.hypermediaresourceapijava.error;

/** Typed API exception carrying a machine-readable code and a safe message. */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
