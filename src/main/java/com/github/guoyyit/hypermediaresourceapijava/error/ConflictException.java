package com.github.guoyyit.hypermediaresourceapijava.error;

/** Typed API exception carrying a machine-readable code and a safe message. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
