package com.github.guoyyit.hypermediaresourceapijava.domain;

/** Immutable order line referencing a product. */
public record OrderItem(String productId, int quantity, long unitPrice) {
}
