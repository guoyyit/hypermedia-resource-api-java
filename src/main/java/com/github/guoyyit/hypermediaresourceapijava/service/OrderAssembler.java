package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderState;
import com.github.guoyyit.hypermediaresourceapijava.domain.Product;
import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles order representations.
 *
 * <p>Documented property order: {@code id}, {@code state}, {@code customerId},
 * {@code version}, {@code createdAt}, {@code updatedAt}, {@code totalPrice},
 * {@code items[].productId}, {@code items[].quantity}, {@code items[].unitPrice}.
 *
 * <p>Links are rendered only when legal in the current state:
 * <pre>
 *   self                  always
 *   orders                always (collection)
 *   customer              always (owner)
 *   pay                   CREATED only
 *   ship                  PAID only
 *   deliver               SHIPPED only
 *   cancel                CREATED or PAID only
 * </pre>
 * Transition links are POST links to the transitions endpoint carrying an
 * {@code action} attribute.
 *
 * <p>Embeds: {@code customer} (single), {@code items} (line objects) with
 * optional nested {@code items.product}. An embed chain that revisits an
 * already-present resource id stops expanding and leaves only the link.
 */
@Component
public class OrderAssembler {

    private final InMemoryStore store;
    private final LinkBuilder links;
    private final ObjectProvider<CustomerAssembler> customerAssembler;
    private final ProductAssembler productAssembler;
    private final HypermediaProperties properties;

    public OrderAssembler(InMemoryStore store, LinkBuilder links,
                          ObjectProvider<CustomerAssembler> customerAssembler,
                          ProductAssembler productAssembler,
                          HypermediaProperties properties) {
        this.store = store;
        this.links = links;
        this.customerAssembler = customerAssembler;
        this.productAssembler = productAssembler;
        this.properties = properties;
    }

    public Resource toResource(Order order, QueryParams params) {
        return toResource(order, params, new LinkedHashSet<>(), EmbedTree.parse(
                params.embed(), Math.min(params.embedDepth(), properties.getEmbedMaxDepth())));
    }

    /** Assemble with an explicit ancestry (cycle guard) and pre-parsed embed tree. */
    Resource toResource(Order order, QueryParams params, Set<String> ancestry, EmbedTree embeds) {
        Set<String> visited = new LinkedHashSet<>(ancestry);
        visited.add("order:" + order.getId());

        Resource r = new Resource();
        Map<String, Object> p = r.getProperties();
        List<String> fields = params.fields();

        put(p, fields, "id", order.getId());
        put(p, fields, "state", order.getState().name().toLowerCase());
        put(p, fields, "customerId", order.getCustomerId());
        put(p, fields, "version", order.getVersion());
        put(p, fields, "createdAt", order.getCreatedAt() == null ? null : order.getCreatedAt().toString());
        put(p, fields, "updatedAt", order.getUpdatedAt() == null ? null : order.getUpdatedAt().toString());
        long total = order.getItems().stream().mapToLong(i -> i.unitPrice() * i.quantity()).sum();
        put(p, fields, "totalPrice", total);

        if (fields == null || fields.contains("items")) {
            List<Map<String, Object>> itemMaps = new java.util.ArrayList<>();
            for (OrderItem item : order.getItems()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("productId", item.productId());
                itemMap.put("quantity", item.quantity());
                itemMap.put("unitPrice", item.unitPrice());
                itemMaps.add(itemMap);
            }
            p.put("items", itemMaps);
        }

        addLinks(r, order);
        addEmbeds(r, order, params, visited, embeds);
        return r;
    }

    private void addLinks(Resource r, Order order) {
        Map<String, List<Link>> l = r.getLinks();
        l.put("self", List.of(new Link("self", links.order(order.getId()))));
        l.put("orders", List.of(new Link("orders", links.orders())));
        l.put("customer", List.of(new Link("customer", links.customer(order.getCustomerId()))));

        String href = links.orderTransitions(order.getId());
        Set<String> allowed = allowedActions(order);
        for (String action : OrderStateService.ACTION_ORDER) {
            if (allowed.contains(action)) {
                l.put(action, List.of(new Link(action, href,
                        Map.of("method", "POST", "action", action,
                                "title", "Execute '" + action + "' transition"))));
            }
        }
    }

    private void addEmbeds(Resource r, Order order, QueryParams params,
                           Set<String> visited, EmbedTree embeds) {
        if (embeds.isEmpty()) {
            return;
        }
        // Sibling order is first-occurrence order in the request, and that is
        // the documented output order of _embedded keys.
        for (Map.Entry<String, EmbedTree.Node> entry : embeds.getRoot().getChildren().entrySet()) {
            String relation = entry.getKey();
            EmbedTree.Node node = entry.getValue();
            switch (relation) {
                case "customer" -> embedCustomer(r, order, params, visited, node);
                case "items" -> embedItems(r, order, params, node, visited);
                default -> { /* unknown relation: best-effort ignore */ }
            }
        }
    }

    private void embedCustomer(Resource r, Order order, QueryParams params,
                               Set<String> visited, EmbedTree.Node customerNode) {
        if (visited.contains("customer:" + order.getCustomerId())) {
            return; // cycle: link already present, do not re-expand
        }
        store.findCustomer(order.getCustomerId()).ifPresent(customer -> {
            Resource sub = customerAssembler.getObject()
                    .toResource(customer, params, visited, customerNode);
            r.getEmbedded().put("customer", List.of(sub));
        });
    }

    private void embedItems(Resource r, Order order, QueryParams params,
                            EmbedTree.Node itemsNode, Set<String> visited) {
        List<Resource> itemResources = new java.util.ArrayList<>();
        EmbedTree.Node productNode = itemsNode.getChildren().get("product");
        for (OrderItem item : order.getItems()) {
            Resource itemResource = new Resource();
            Map<String, Object> ip = itemResource.getProperties();
            ip.put("productId", item.productId());
            ip.put("quantity", item.quantity());
            ip.put("unitPrice", item.unitPrice());
            itemResource.getLinks().put("self", List.of(
                    new Link("self", links.order(order.getId()) + "#item-" + item.productId())));
            itemResource.getLinks().put("product", List.of(
                    new Link("product", links.product(item.productId()))));

            if (productNode != null && !visited.contains("product:" + item.productId())) {
                Product product = store.findProduct(item.productId()).orElse(null);
                if (product != null) {
                    Resource productResource = productAssembler.toResource(product,
                            new QueryParams(java.util.List.of(), params.embedDepth(),
                                    params.fields(), params.page(), params.size()));
                    itemResource.getEmbedded().put("product", List.of(productResource));
                }
            }
            itemResources.add(itemResource);
        }
        r.getEmbedded().put("items", itemResources);
    }

    /** Actions legal in the given state, deterministic order via ACTION_ORDER. */
    Set<String> allowedActions(Order order) {
        Set<String> allowed = new LinkedHashSet<>();
        for (String action : OrderStateService.ACTION_ORDER) {
            if (OrderStateService.TRANSITIONS.get(action).containsKey(order.getState())) {
                allowed.add(action);
            }
        }
        return allowed;
    }

    private static void put(Map<String, Object> properties, List<String> fields,
                            String name, Object value) {
        if (fields == null || fields.contains(name)) {
            properties.put(name, value);
        }
    }
}
