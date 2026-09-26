package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import org.springframework.stereotype.Component;

/**
 * Builds deterministic hrefs for every route. All hrefs are absolute paths
 * starting with the configured {@code hypermedia.base-path} (default empty,
 * i.e. application-root relative, e.g. {@code /api/orders/o-1}).
 *
 * <p>Route catalogue (the single source of truth shared by controllers and
 * assemblers):
 * <pre>
 *   GET    /                              home
 *   GET    /customers                     customer list
 *   POST   /customers                     create customer
 *   GET    /customers/{id}                customer
 *   PUT    /customers/{id}                update customer
 *   GET    /customers/{id}/orders         orders belonging to a customer
 *   GET    /orders                        order list
 *   POST   /orders                        create order
 *   GET    /orders/{id}                   order
 *   POST   /orders/{id}/transitions       execute a transition {action}
 *   GET    /products                      product list
 *   GET    /products/{id}                 product
 * </pre>
 */
@Component
public class LinkBuilder {

    private final HypermediaProperties properties;

    public LinkBuilder(HypermediaProperties properties) {
        this.properties = properties;
    }

    private String base() {
        String value = properties.getBasePath() == null ? "" : properties.getBasePath().trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String path(String suffix) {
        return base() + suffix;
    }

    /** Encode path segments (ids are application-generated, kept simple). */
    private String segment(String id) {
        return java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String home() {
        return path("/");
    }

    public String customers() {
        return path("/customers");
    }

    public String customer(String id) {
        return path("/customers/" + segment(id));
    }

    public String customerOrders(String id) {
        return path("/customers/" + segment(id) + "/orders");
    }

    public String orders() {
        return path("/orders");
    }

    public String order(String id) {
        return path("/orders/" + segment(id));
    }

    public String orderTransitions(String id) {
        return path("/orders/" + segment(id) + "/transitions");
    }

    public String products() {
        return path("/products");
    }

    public String product(String id) {
        return path("/products/" + segment(id));
    }
}
