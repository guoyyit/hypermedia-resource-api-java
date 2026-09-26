package com.github.guoyyit.hypermediaresourceapijava.config;

import com.github.guoyyit.hypermediaresourceapijava.domain.Customer;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderState;
import com.github.guoyyit.hypermediaresourceapijava.domain.Product;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds deterministic demo data. Fixed ids and timestamps keep listing order,
 * ETags and navigation tests reproducible.
 *
 * <p>Seed set:
 * <ul>
 *   <li>customers: c-1 (Alice), c-2 (Bob);</li>
 *   <li>products: p-1 Widget (1000), p-2 Gadget (2500), p-3 Gizmo (500);</li>
 *   <li>orders: o-1 CREATED (Alice, widget), o-2 PAID (Alice, gadget),
 *       o-3 SHIPPED (Bob, gizmo+widget), o-4 DELIVERED (Bob, gadget),
 *       o-5 CANCELLED (Alice, gizmo). The spread of states drives link
 *       visibility tests; the six-row total with default page size 5 drives
 *       pagination tests.</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final InMemoryStore store;

    public DataInitializer(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public void run(String... args) {
        Instant base = Instant.parse("2026-09-01T08:00:00Z");

        store.withWriteLock(() -> {
            seedCustomers(base);
            seedProducts();
            seedOrders(base);
        });
    }

    private void seedCustomers(Instant base) {
        Customer alice = new Customer();
        alice.setId("c-1");
        alice.setName("Alice Anderson");
        alice.setEmail("alice@example.com");
        alice.setVersion(0);
        alice.setCreatedAt(base);
        alice.setUpdatedAt(base);
        store.saveCustomer(alice);

        Customer bob = new Customer();
        bob.setId("c-2");
        bob.setName("Bob Brown");
        bob.setEmail("bob@example.com");
        bob.setVersion(0);
        bob.setCreatedAt(base);
        bob.setUpdatedAt(base);
        store.saveCustomer(bob);
    }

    private void seedProducts() {
        store.saveProduct(new Product("p-1", "Widget", 1000));
        store.saveProduct(new Product("p-2", "Gadget", 2500));
        store.saveProduct(new Product("p-3", "Gizmo", 500));
    }

    private void seedOrders(Instant base) {
        createOrder("o-1", "c-1", OrderState.CREATED, 0,
                base.plusSeconds(10), List.of(new OrderItem("p-1", 1, 1000)));
        createOrder("o-2", "c-1", OrderState.PAID, 1,
                base.plusSeconds(20), List.of(new OrderItem("p-2", 2, 2500)));
        createOrder("o-3", "c-2", OrderState.SHIPPED, 2,
                base.plusSeconds(30),
                List.of(new OrderItem("p-3", 3, 500), new OrderItem("p-1", 1, 1000)));
        createOrder("o-4", "c-2", OrderState.DELIVERED, 3,
                base.plusSeconds(40), List.of(new OrderItem("p-2", 1, 2500)));
        createOrder("o-5", "c-1", OrderState.CANCELLED, 1,
                base.plusSeconds(50), List.of(new OrderItem("p-3", 5, 500)));
    }

    private void createOrder(String id, String customerId, OrderState state, long version,
                             Instant at, List<OrderItem> items) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setItems(items);
        order.setState(state);
        order.setVersion(version);
        order.setCreatedAt(at);
        order.setUpdatedAt(at.plusSeconds(version));
        store.saveOrder(order);
    }
}
