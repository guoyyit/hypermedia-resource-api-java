package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderStatus;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryOrderStore;
import com.github.guoyyit.hypermediaresourceapijava.web.dto.OrderRequest;
import com.github.guoyyit.hypermediaresourceapijava.web.error.BadRequestException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ConflictException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command side.
 *
 * Idempotency / atomicity contract:
 * <ul>
 *   <li>every state change runs through {@link InMemoryOrderStore#mutate},
 *       which validates the optimistic version under the aggregate lock and
 *       commits only on success: no intermediate state survives a failure;</li>
 *   <li>a transition is re-validated against the current in-lock status, so a
 *       repeated submission of the same transition after it succeeded has no
 *       matching control and is rejected rather than changing state twice
 *       (it also fails the version check with 412 if the same ETag is used);</li>
 *   <li>field updates are allowed only on DRAFT orders (the only state that
 *       exposes the edit control).</li>
 * </ul>
 */
@Service
public class OrderActionService {

    private final InMemoryOrderStore store;

    public OrderActionService(InMemoryOrderStore store) {
        this.store = store;
    }

    public Order create(String customerName, List<OrderRequest.ItemInput> items) {
        Order order = new Order();
        order.setStatus(OrderStatus.DRAFT);
        order.setCustomerName(requireCustomer(customerName));
        order.setItems(validateItems(items, order));
        order.setUpdatedAt(Instant.now());
        return store.create(order);
    }

    public Order update(String id, long expectedVersion, String customerName,
                        List<OrderRequest.ItemInput> items) {
        Order existing = store.findById(id)
                .orElseThrow(() -> notFound(id));
        if (existing.getStatus() != OrderStatus.DRAFT) {
            throw new ConflictException(
                    "UPDATE_NOT_ALLOWED",
                    "Order fields can only be edited while the order is DRAFT",
                    Map.of("self", "/orders/" + id, "orders", "/orders"));
        }
        Order prototype = existing.copy();
        prototype.setCustomerName(requireCustomer(customerName));
        // Replace the item set atomically. Back-references are rewired.
        prototype.setItems(validateItems(items, prototype));
        return commit(id, expectedVersion, o -> prototype);
    }

    public Order transition(String id, long expectedVersion, String action) {
        // Read first purely to produce a 404 with navigable links for unknown ids.
        store.findById(id).orElseThrow(() -> notFound(id));
        String normalized = action == null ? "" : action.trim();
        if (!List.of("submit", "pay", "ship", "deliver", "cancel").contains(normalized)) {
            throw new BadRequestException(
                    "UNKNOWN_ACTION",
                    "Unknown transition action: " + action,
                    Map.of("self", "/orders/" + id, "orders", "/orders"));
        }
        return commit(id, expectedVersion, order -> {
            // Re-checked under the lock against the authoritative status.
            OrderStatus target = order.getStatus().after(normalized)
                    .orElseThrow(() -> new ConflictException(
                            "ILLEGAL_TRANSITION",
                            "Action '" + normalized + "' is not available in status "
                                    + order.getStatus(),
                            Map.of("self", "/orders/" + id, "orders", "/orders")));
            order.setStatus(target);
            return order;
        });
    }

    private Order commit(String id, long expectedVersion,
                         java.util.function.UnaryOperator<Order> mutator) {
        try {
            return store.mutate(id, expectedVersion, mutator);
        } catch (InMemoryOrderStore.OptimisticVersionException e) {
            throw new com.github.guoyyit.hypermediaresourceapijava.web.error.PreconditionFailedException(
                    "VERSION_CONFLICT",
                    "The order was modified concurrently; refetch the current state and retry",
                    Map.of("self", "/orders/" + id,
                            "orders", "/orders",
                            "retry", "/orders/" + id));
        } catch (InMemoryOrderStore.OrderNotFoundException e) {
            throw notFound(id);
        }
    }

    private ResourceNotFoundException notFound(String id) {
        return new ResourceNotFoundException(
                "ORDER_NOT_FOUND",
                "No order exists at /orders/" + id,
                Map.of("self", "/orders/" + id, "orders", "/orders"));
    }

    private String requireCustomer(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "customerName is required and must not be blank",
                    Map.of("self", "/orders", "orders", "/orders"));
        }
        return customerName.trim();
    }

    private List<OrderItem> validateItems(List<OrderRequest.ItemInput> inputs, Order owner) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "items must contain at least one line item",
                    Map.of("self", "/orders", "orders", "/orders"));
        }
        List<OrderItem> items = new ArrayList<>();
        int index = 1;
        for (OrderRequest.ItemInput input : inputs) {
            if (input == null || input.sku() == null || input.sku().isBlank()
                    || input.name() == null || input.name().isBlank()
                    || input.quantity() == null || input.quantity() < 1
                    || input.unitPrice() == null || input.unitPrice() < 0) {
                throw new BadRequestException(
                        "VALIDATION_ERROR",
                        "Item at position " + index
                                + " is invalid; sku/name required, quantity>=1, unitPrice>=0",
                        Map.of("self", "/orders", "orders", "/orders"));
            }
            OrderItem item = new OrderItem();
            item.setSku(input.sku().trim());
            item.setName(input.name().trim());
            item.setQuantity(input.quantity());
            item.setUnitPrice(input.unitPrice());
            item.setOrder(owner);
            items.add(item);
            index++;
        }
        return items;
    }
}
