package com.github.guoyyit.hypermediaresourceapijava.store;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds deterministic demo orders (ids O-1001 .. O-1006) covering every
 * status, including a cycle-free-but-back-referencing item -> order graph
 * used to demonstrate bounded embedding.
 */
@Component
public class SeedDataLoader implements CommandLineRunner {

    private final InMemoryOrderStore store;
    private final HypermediaProperties properties;

    public SeedDataLoader(InMemoryOrderStore store, HypermediaProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.getData().isSeedOnStartup()) {
            return;
        }
        seed("Alice", OrderStatus.DRAFT,
                item("SKU-1", "Keyboard", 1, 4999), item("SKU-2", "Mouse", 2, 1299));
        seed("Bob", OrderStatus.PENDING_PAYMENT,
                item("SKU-3", "Monitor", 1, 18900));
        seed("Carol", OrderStatus.PAID,
                item("SKU-4", "Webcam", 1, 3499));
        seed("Dave", OrderStatus.SHIPPED,
                item("SKU-5", "Desk Lamp", 3, 899));
        seed("Erin", OrderStatus.DELIVERED,
                item("SKU-6", "Headset", 1, 7200));
        seed("Frank", OrderStatus.CANCELLED,
                item("SKU-7", "Cable", 5, 199));
    }

    private void seed(String customer, OrderStatus status, OrderItem... items) {
        Order order = new Order();
        order.setCustomerName(customer);
        order.setStatus(status);
        List<OrderItem> list = new ArrayList<>();
        for (OrderItem item : items) {
            item.setOrder(order);
            list.add(item);
        }
        order.setItems(list);
        Order saved = store.create(order);
        // store.create assigns sequential ids O-1001.., good enough for seeding.
        if (saved == null) {
            throw new IllegalStateException("seeding failed");
        }
    }

    private static OrderItem item(String sku, String name, int qty, long unitPrice) {
        OrderItem item = new OrderItem();
        item.setSku(sku);
        item.setName(name);
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        return item;
    }
}
