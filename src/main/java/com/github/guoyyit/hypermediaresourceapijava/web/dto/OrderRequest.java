package com.github.guoyyit.hypermediaresourceapijava.web.dto;

import java.util.List;

/**
 * Write model for create/update. Validation is performed explicitly in the
 * service so failures map to the documented error contract.
 */
public record OrderRequest(String customerName, List<ItemInput> items) {

    public record ItemInput(String sku, String name, Integer quantity, Long unitPrice) {
    }
}
