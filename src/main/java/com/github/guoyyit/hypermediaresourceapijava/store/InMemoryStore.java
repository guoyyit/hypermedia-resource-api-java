package com.github.guoyyit.hypermediaresourceapijava.store;

import com.github.guoyyit.hypermediaresourceapijava.domain.Customer;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.Product;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Single-JVM in-memory store.
 *
 * <p>Consistency model:
 * <ul>
 *   <li>one read/write lock guards all aggregate maps (correctness over
 *       throughput on this single-node implementation);</li>
 *   <li>mutations run inside {@link #withWriteLock(Supplier)} so a transition
 *       that validates then writes is atomic and no half-applied state can be
 *       observed by a concurrent reader;</li>
 *   <li>read accessors return detached snapshots, so callers can never mutate
 *       stored aggregates through their references;</li>
 *   <li>list accessors return a stable ordering: natural insertion order for
 *       products and customers, orders sorted by (createdAt asc, id asc).</li>
 * </ul>
 */
@Component
public class InMemoryStore {

    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private static final Comparator<Order> ORDER_ORDERING =
            Comparator.comparing(Order::getCreatedAt).thenComparing(Order::getId);

    // ---------------------------------------------------------------- orders

    public Optional<Order> findOrder(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(orders.get(id)).map(InMemoryStore::copy);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Order> findAllOrders() {
        lock.readLock().lock();
        try {
            List<Order> snapshot = orders.values().stream().map(InMemoryStore::copy).toList();
            return snapshot.stream().sorted(ORDER_ORDERING).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Insert or replace; must be invoked inside {@link #withWriteLock}. */
    public void saveOrder(Order order) {
        orders.put(order.getId(), copy(order));
    }

    // -------------------------------------------------------------- customers

    public Optional<Customer> findCustomer(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(customers.get(id)).map(InMemoryStore::copy);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Customer> findAllCustomers() {
        lock.readLock().lock();
        try {
            return customers.values().stream().map(InMemoryStore::copy).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveCustomer(Customer customer) {
        customers.put(customer.getId(), copy(customer));
    }

    // --------------------------------------------------------------- products

    public Optional<Product> findProduct(String id) {
        lock.readLock().lock();
        try {
            // Product is immutable; reference sharing is safe.
            return Optional.ofNullable(products.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Product> findAllProducts() {
        lock.readLock().lock();
        try {
            return List.copyOf(products.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveProduct(Product product) {
        products.put(product.getId(), product);
    }

    // ------------------------------------------------------------- locking

    /** Run an action while holding the exclusive store lock. */
    public void withWriteLock(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Run a function while holding the exclusive store lock. */
    public <T> T withWriteLock(Supplier<T> action) {
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Aggregate-scoped critical section. The id is currently mapped onto the
     * store-wide lock; keeping the id in the API lets callers express the
     * intended granularity.
     */
    public void withOrderLock(String id, Runnable action) {
        withWriteLock(action);
    }

    public <T> T withOrderLock(String id, Supplier<T> action) {
        return withWriteLock(action);
    }

    // --------------------------------------------------------------- copies

    private static Order copy(Order source) {
        Order target = new Order();
        target.setId(source.getId());
        target.setCustomerId(source.getCustomerId());
        target.setItems(new ArrayList<>(source.getItems()));
        target.setState(source.getState());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private static Customer copy(Customer source) {
        Customer target = new Customer();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setEmail(source.getEmail());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
