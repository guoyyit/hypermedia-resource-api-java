package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.domain.Customer;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles customer representations.
 *
 * <p>Documented property order: {@code id}, {@code name}, {@code email},
 * {@code version}, {@code createdAt}, {@code updatedAt}.
 * Links (documented order): {@code self}, {@code customers}, {@code orders}
 * (this customer's order collection), {@code order-create} (POST /orders).
 *
 * <p>Embeds: {@code orders} lists the customer's orders in store order
 * (createdAt asc, id asc), truncated only by the embed-depth budget. Cycle
 * chains such as customer -> orders -> customer stop expanding when the
 * customer id already occurs on the ancestry chain, leaving the link intact.
 */
@Component
public class CustomerAssembler {

    private final InMemoryStore store;
    private final LinkBuilder links;
    private final OrderAssembler orderAssembler;
    private final HypermediaProperties properties;

    public CustomerAssembler(InMemoryStore store, LinkBuilder links,
                             OrderAssembler orderAssembler,
                             HypermediaProperties properties) {
        this.store = store;
        this.links = links;
        this.orderAssembler = orderAssembler;
        this.properties = properties;
    }

    public Resource toResource(Customer customer, QueryParams params) {
        return toResource(customer, params, new LinkedHashSet<>(), EmbedTree.parse(
                params.embed(), Math.min(params.embedDepth(), properties.getEmbedMaxDepth())).getRoot());
    }

    /** Assemble from a parent embed node with an explicit ancestry set. */
    Resource toResource(Customer customer, QueryParams params,
                        Set<String> ancestry, EmbedTree.Node node) {
        Set<String> visited = new LinkedHashSet<>(ancestry);
        visited.add("customer:" + customer.getId());

        Resource r = new Resource();
        Map<String, Object> p = r.getProperties();
        List<String> fields = params.fields();
        put(p, fields, "id", customer.getId());
        put(p, fields, "name", customer.getName());
        put(p, fields, "email", customer.getEmail());
        put(p, fields, "version", customer.getVersion());
        put(p, fields, "createdAt", customer.getCreatedAt() == null ? null : customer.getCreatedAt().toString());
        put(p, fields, "updatedAt", customer.getUpdatedAt() == null ? null : customer.getUpdatedAt().toString());

        Map<String, List<Link>> l = r.getLinks();
        l.put("self", List.of(new Link("self", links.customer(customer.getId()))));
        l.put("customers", List.of(new Link("customers", links.customers())));
        l.put("orders", List.of(new Link("orders", links.customerOrders(customer.getId()))));
        l.put("order-create", List.of(new Link("order-create", links.orders(),
                Map.of("method", "POST", "title", "Create a new order"))));

        if (node != null) {
            EmbedTree.Node ordersNode = node.getChildren().get("orders");
            if (ordersNode != null) {
                List<Resource> orderResources = new java.util.ArrayList<>();
                for (Order order : store.findAllOrders()) {
                    if (!customer.getId().equals(order.getCustomerId())) {
                        continue;
                    }
                    if (visited.contains("order:" + order.getId())) {
                        // Cycle: link already present on the other resource;
                        // do not re-expand.
                        continue;
                    }
                    orderResources.add(orderAssembler.toResource(order, params, visited, subtree(ordersNode)));
                }
                r.getEmbedded().put("orders", orderResources);
            }
        }
        return r;
    }

    /** Rebuild a standalone tree rooted at this node for the child assembler. */
    private EmbedTree subtree(EmbedTree.Node node) {
        EmbedTree tree = EmbedTree.parse(java.util.List.of(), properties.getEmbedMaxDepth());
        copyNode(node, tree.getRoot());
        return tree;
    }

    private void copyNode(EmbedTree.Node source, EmbedTree.Node targetParent) {
        for (Map.Entry<String, EmbedTree.Node> entry : source.getChildren().entrySet()) {
            EmbedTree.Node copy = new EmbedTree.Node(entry.getKey(),
                    targetParent.getDepth() + 1);
            targetParent.getChildren().put(entry.getKey(), copy);
            copyNode(entry.getValue(), copy);
        }
    }

    private static void put(Map<String, Object> properties, List<String> fields,
                            String name, Object value) {
        if (fields == null || fields.contains(name)) {
            properties.put(name, value);
        }
    }
}
