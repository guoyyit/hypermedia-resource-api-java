package com.github.guoyyit.hypermediaresourceapijava.error;

/** Typed API exception carrying a machine-readable code and a safe message. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
