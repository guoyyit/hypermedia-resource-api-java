package com.github.guoyyit.hypermediaresourceapijava.error;

/** Typed API exception carrying a machine-readable code and a safe message. */
public class PreconditionFailedException extends RuntimeException {
    public PreconditionFailedException(String message) {
        super(message);
    }
}
