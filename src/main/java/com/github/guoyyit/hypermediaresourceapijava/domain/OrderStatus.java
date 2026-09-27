package com.github.guoyyit.hypermediaresourceapijava.domain;

import java.util.Optional;

/**
 * Lifecycle state of an order.
 *
 * <pre>
 * DRAFT ──submit──▶ PENDING_PAYMENT ──pay──▶ PAID ──ship──▶ SHIPPED ──deliver──▶ DELIVERED
 *   │                    │
 *   └──cancel──▶ CANCELLED ◀──cancel──┘
 * </pre>
 *
 * DELIVERED and CANCELLED are terminal: they expose no transition controls.
 */
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Resulting state for an action name, or empty if the action is not legal
     * in this state. This single table is the only place that defines which
     * transitions exist, so link visibility and transition execution can never
     * disagree.
     */
    public Optional<OrderStatus> after(String action) {
        if (action == null) {
            return Optional.empty();
        }
        return switch (this) {
            case DRAFT -> switch (action) {
                case "submit" -> Optional.of(PENDING_PAYMENT);
                case "cancel" -> Optional.of(CANCELLED);
                default -> Optional.empty();
            };
            case PENDING_PAYMENT -> switch (action) {
                case "pay" -> Optional.of(PAID);
                case "cancel" -> Optional.of(CANCELLED);
                default -> Optional.empty();
            };
            case PAID -> "ship".equals(action) ? Optional.of(SHIPPED) : Optional.empty();
            case SHIPPED -> "deliver".equals(action) ? Optional.of(DELIVERED) : Optional.empty();
            case DELIVERED, CANCELLED -> Optional.empty();
        };
    }

    /** Human-readable hint attached to the transition control. */
    public String actionTitle(String action) {
        return switch (action) {
            case "submit" -> "Submit order for payment";
            case "pay" -> "Mark order as paid";
            case "ship" -> "Mark order as shipped";
            case "deliver" -> "Mark order as delivered";
            case "cancel" -> "Cancel order";
            default -> null;
        };
    }
}
