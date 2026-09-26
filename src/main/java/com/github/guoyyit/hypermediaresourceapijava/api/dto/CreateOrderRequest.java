package com.github.guoyyit.hypermediaresourceapijava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Body of POST /orders. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {

    private String customerId;
    private List<Line> items;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<Line> getItems() { return items; }
    public void setItems(List<Line> items) { this.items = items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Line {
        private String productId;
        private Integer quantity;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
