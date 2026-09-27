package com.github.guoyyit.hypermediaresourceapijava.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root. Mutable, guarded by the store lock during state changes.
 * {@code version} is an optimistic concurrency counter; it is also the ETag
 * discriminator.
 */
public class Order {

    private String id;
    private OrderStatus status;
    private String customerName;
    private List<OrderItem> items = new ArrayList<>();
    private long version = 0;
    private Instant updatedAt;

    public Order() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Deep copy used for stable, mutation-free reads outside the store lock.
     * Item back-references are rewired to the copied aggregate.
     */
    public Order copy() {
        Order c = new Order();
        c.id = id;
        c.status = status;
        c.customerName = customerName;
        c.version = version;
        c.updatedAt = updatedAt;
        List<OrderItem> copiedItems = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItem ic = item.copy();
            ic.setOrder(c);
            copiedItems.add(ic);
        }
        c.items = copiedItems;
        return c;
    }
}
