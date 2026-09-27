package com.github.guoyyit.hypermediaresourceapijava.hyper;

/**
 * Supported wire representations. All variants carry identical semantics
 * and state; they differ only in field layout/name.
 */
public enum MediaTypeKind {
    HAL("application/hal+json"),
    VND("application/vnd.hypermedia+json");

    private final String value;

    MediaTypeKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
