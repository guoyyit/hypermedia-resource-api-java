package com.github.guoyyit.hypermediaresourceapijava.domain;

/**
 * One line item of an order. Items are owned by their order and expose it
 * via {@link #getOrder()} so that item -> order embedding forms a graph.
 */
public class OrderItem {

    private String id;
    private String sku;
    private String name;
    private int quantity;
    private long unitPrice;
    private Order order;

    public OrderItem() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    /**
     * Shallow copy of the line item fields. The owning order reference is
     * intentionally NOT copied here; callers owning an aggregate wire it up
     * after copying to avoid accidentally sharing detached item graphs.
     */
    public OrderItem copy() {
        OrderItem c = new OrderItem();
        c.id = id;
        c.sku = sku;
        c.name = name;
        c.quantity = quantity;
        c.unitPrice = unitPrice;
        return c;
    }
}
