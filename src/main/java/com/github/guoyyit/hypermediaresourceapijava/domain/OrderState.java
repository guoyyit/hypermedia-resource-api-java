package com.github.guoyyit.hypermediaresourceapijava.domain;

/**
 * Lifecycle state of an order resource.
 * Allowed transitions (see OrderStateService):
 *   CREATED   -> pay     -> PAID
 *   PAID      -> ship    -> SHIPPED
 *   SHIPPED   -> deliver -> DELIVERED
 *   CREATED/PAID -> cancel -> CANCELLED
 */
public enum OrderState {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
