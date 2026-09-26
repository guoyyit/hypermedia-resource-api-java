package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.api.dto.CreateOrderRequest;
import com.github.guoyyit.hypermediaresourceapijava.domain.Customer;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderState;
import com.github.guoyyit.hypermediaresourceapijava.domain.Product;
import com.github.guoyyit.hypermediaresourceapijava.error.ConflictException;
import com.github.guoyyit.hypermediaresourceapijava.error.NotFoundException;
import com.github.guoyyit.hypermediaresourceapijava.error.PreconditionFailedException;
import com.github.guoyyit.hypermediaresourceapijava.error.UnprocessableEntityException;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Order state machine.
 *
 * <p>Legal transition table (the only transitions whose links are rendered):
 * <pre>
 *   CREATED   --pay-->     PAID
 *   PAID      --ship-->    SHIPPED
 *   SHIPPED   --deliver--> DELIVERED
 *   CREATED   --cancel-->  CANCELLED
 *   PAID      --cancel-->  CANCELLED
 * </pre>
 * Each transition is validated and applied inside the store write lock, so a
 * failed validation never mutates state and concurrent transitions cannot
 * interleave. The optimistic {@code version} backs If-Match concurrency
 * control; an Idempotency-Key makes repeated submissions no-ops that replay
 * the first outcome.
 */
@Service
public class OrderStateService {

    /** action -> (from state -> to state); iteration order is the documented link order. */
    static final Map<String, Map<OrderState, OrderState>> TRANSITIONS = Map.of(
            "pay", Map.of(OrderState.CREATED, OrderState.PAID),
            "ship", Map.of(OrderState.PAID, OrderState.SHIPPED),
            "deliver", Map.of(OrderState.SHIPPED, OrderState.DELIVERED),
            "cancel", Map.of(OrderState.CREATED, OrderState.CANCELLED,
                    OrderState.PAID, OrderState.CANCELLED)
    );

    /** Deterministic rendering order of transition links. */
    static final List<String> ACTION_ORDER = List.of("pay", "ship", "deliver", "cancel");

    private final InMemoryStore store;
    private final IdempotencyService idempotencyService;
    private final ConditionalRequests conditional;

    public OrderStateService(InMemoryStore store,
                             IdempotencyService idempotencyService,
                             ConditionalRequests conditional) {
        this.store = store;
        this.idempotencyService = idempotencyService;
        this.conditional = conditional;
    }

    /** Result of a state-changing call: target order id and HTTP status to emit. */
    public record ChangeResult(String orderId, int status, boolean replayed) {
    }

    /**
     * Apply a named transition.
     *
     * @param ifMatch optional If-Match header; when present it must match the
     *                current order ETag or a 412 is returned (no state change)
     */
    public ChangeResult transition(String orderId, String action,
                                   String idempotencyKey, String ifMatch) {
        if (action == null || action.isBlank()) {
            throw new UnprocessableEntityException("Transition request must include an 'action'.");
        }
        if (!TRANSITIONS.containsKey(action)) {
            // Unknown action: safe to reject before idempotency bookkeeping.
            throw new ConflictException("Unknown transition action '" + action + "'.");
        }

        String fingerprint = "transition:" + orderId + ":" + action;
        Optional<IdempotencyService.Replay> replay = beginKeyed(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return new ChangeResult(replay.get().resourceId(), replay.get().status(), true);
        }

        try {
            ChangeResult result = store.withWriteLock(() -> {
                Order order = store.findOrder(orderId)
                        .orElseThrow(() -> new NotFoundException("Order '" + orderId + "' does not exist."));

                if (ifMatch != null && !ifMatch.isBlank()
                        && !conditional.ifMatchPasses(ifMatch,
                                conditional.etag(order.getId(), order.getVersion()))) {
                    throw new PreconditionFailedException(
                            "If-Match precondition failed; the order was modified by another request.");
                }

                OrderState target = TRANSITIONS.get(action).get(order.getState());
                if (target == null) {
                    throw new ConflictException(
                            "Transition '" + action + "' is not legal from state "
                                    + order.getState() + ".");
                }

                Instant now = Instant.now();
                order.setState(target);
                order.setVersion(order.getVersion() + 1);
                order.setUpdatedAt(now);
                store.saveOrder(order);
                return new ChangeResult(order.getId(), 200, false);
            });

            idempotencyService.complete(idempotencyKey, fingerprint, result.orderId(), result.status());
            return result;
        } catch (RuntimeException e) {
            idempotencyService.fail(idempotencyKey);
            throw e;
        }
    }

    /** Create an order from the request; validates references atomically. */
    public ChangeResult create(CreateOrderRequest request, String idempotencyKey) {
        if (request == null || request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            throw new UnprocessableEntityException("Order request must include a 'customerId'.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new UnprocessableEntityException("Order request must include at least one item.");
        }
        for (CreateOrderRequest.Line line : request.getItems()) {
            if (line.getProductId() == null || line.getProductId().isBlank()) {
                throw new UnprocessableEntityException("Each order item must include a 'productId'.");
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new UnprocessableEntityException("Each order item quantity must be a positive integer.");
            }
        }

        String fingerprint = "create-order:" + request.getCustomerId() + ":"
                + request.getItems().stream()
                .map(l -> l.getProductId() + "x" + l.getQuantity())
                .reduce((a, b) -> a + "," + b).orElse("");

        Optional<IdempotencyService.Replay> replay = beginKeyed(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return new ChangeResult(replay.get().resourceId(), replay.get().status(), true);
        }

        try {
            ChangeResult result = store.withWriteLock(() -> {
                Customer customer = store.findCustomer(request.getCustomerId())
                        .orElseThrow(() -> new UnprocessableEntityException(
                                "Customer '" + request.getCustomerId() + "' does not exist."));

                List<OrderItem> items = new java.util.ArrayList<>();
                for (CreateOrderRequest.Line line : request.getItems()) {
                    Product product = store.findProduct(line.getProductId())
                            .orElseThrow(() -> new UnprocessableEntityException(
                                    "Product '" + line.getProductId() + "' does not exist."));
                    items.add(new OrderItem(product.getId(), line.getQuantity(), product.getPrice()));
                }

                Order order = new Order();
                String id = "o-" + System.nanoTime();
                Instant now = Instant.now();
                order.setId(id);
                order.setCustomerId(customer.getId());
                order.setItems(items);
                order.setState(OrderState.CREATED);
                order.setVersion(0);
                order.setCreatedAt(now);
                order.setUpdatedAt(now);
                store.saveOrder(order);
                return new ChangeResult(id, 201, false);
            });

            idempotencyService.complete(idempotencyKey, fingerprint, result.orderId(), result.status());
            return result;
        } catch (RuntimeException e) {
            idempotencyService.fail(idempotencyKey);
            throw e;
        }
    }

    private Optional<IdempotencyService.Replay> beginKeyed(String key, String fingerprint) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return idempotencyService.begin(key, fingerprint);
    }
}
