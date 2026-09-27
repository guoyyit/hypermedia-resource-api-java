package com.github.guoyyit.hypermediaresourceapijava.store;

import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * In-memory, thread-safe order aggregate store.
 *
 * Concurrency contract:
 * <ul>
 *   <li>each aggregate id has a stable monitor; reads copy under that monitor,
 *       writes mutate under it, so a read never observes a torn aggregate;</li>
 *   <li>the mutator runs against a detached copy. The aggregate is replaced
 *       only after the mutator returns normally, so a failed or aborted change
 *       leaves no intermediate state;</li>
 *   <li>every successful write increments {@code version} and stamps
 *       {@code updatedAt} exactly once, atomically with the change;</li>
 *   <li>{@code expectedVersion} is compared before the mutator runs; a mismatch
 *       fails with {@link OptimisticVersionException} and changes nothing.</li>
 * </ul>
 */
@Component
public class InMemoryOrderStore {

    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1000);

    private Object lockFor(String id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    public Optional<Order> findById(String id) {
        Order order = orders.get(id);
        if (order == null) {
            return Optional.empty();
        }
        synchronized (lockFor(id)) {
            return Optional.of(order.copy());
        }
    }

    /**
     * Snapshot of all orders, sorted by id ascending for stable iteration.
     * Each aggregate is copied under its own monitor.
     */
    public List<Order> findAll() {
        List<Order> snapshot = new ArrayList<>();
        for (String id : new ArrayList<>(orders.keySet())) {
            Order order = orders.get(id);
            if (order != null) {
                synchronized (lockFor(id)) {
                    snapshot.add(order.copy());
                }
            }
        }
        snapshot.sort(Comparator.comparing(Order::getId));
        return snapshot;
    }

    /** Insert a new aggregate; assigns its id, initial version and timestamps. */
    public Order create(Order prototype) {
        String id = "O-" + idSequence.incrementAndGet();
        synchronized (lockFor(id)) {
            prototype.setId(id);
            prototype.setVersion(1);
            prototype.setUpdatedAt(Instant.now());
            assignItemIds(prototype);
            orders.put(id, prototype);
            return prototype.copy();
        }
    }

    /**
     * Apply {@code mutator} atomically iff the current version equals
     * {@code expectedVersion}.
     *
     * @throws OrderNotFoundException     if the id is unknown
     * @throws OptimisticVersionException if the version no longer matches
     */
    public Order mutate(String id, long expectedVersion, UnaryOperator<Order> mutator) {
        Object monitor = lockFor(id);
        synchronized (monitor) {
            Order current = orders.get(id);
            if (current == null) {
                throw new OrderNotFoundException(id);
            }
            if (current.getVersion() != expectedVersion) {
                throw new OptimisticVersionException(id, expectedVersion, current.getVersion());
            }
            // Mutate a detached copy; any exception leaves the stored aggregate untouched.
            Order candidate = mutator.apply(current.copy());
            candidate.setId(id);
            candidate.setVersion(current.getVersion() + 1);
            candidate.setUpdatedAt(Instant.now());
            assignItemIds(candidate);
            orders.put(id, candidate);
            return candidate.copy();
        }
    }

    /**
     * Deterministic per-aggregate line item ids by list position
     * ({@code <orderId>-I001} ...). Zero padding keeps id sort equal to
     * position sort; re-numbering on every write keeps ids aligned with the
     * replaced item set after an update.
     */
    private void assignItemIds(Order order) {
        int index = 1;
        for (OrderItem item : order.getItems()) {
            item.setId(String.format("%s-I%03d", order.getId(), index));
            item.setOrder(order);
            index++;
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String id) {
            super(id);
        }
    }

    public static class OptimisticVersionException extends RuntimeException {
        private final long expected;
        private final long actual;

        public OptimisticVersionException(String id, long expected, long actual) {
            super("Order " + id + " was modified concurrently");
            this.expected = expected;
            this.actual = actual;
        }

        public long getExpected() {
            return expected;
        }

        public long getActual() {
            return actual;
        }
    }
}
