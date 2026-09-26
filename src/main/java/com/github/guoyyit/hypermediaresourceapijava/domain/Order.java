package com.github.guoyyit.hypermediaresourceapijava.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Mutable order aggregate; guarded by its own monitor inside the store. */
public class Order {

    private String id;
    private String customerId;
    private List<OrderItem> items = new ArrayList<>();
    private OrderState state = OrderState.CREATED;
    private long version = 0;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public OrderState getState() { return state; }
    public void setState(OrderState state) { this.state = state; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
