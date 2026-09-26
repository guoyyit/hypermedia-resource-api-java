package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.domain.Product;
import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Assembles immutable product representations.
 *
 * <p>Documented property order: {@code id}, {@code name}, {@code price}.
 * Links (documented order): {@code self}, {@code products} (collection).
 * Products have no state-dependent transitions.
 */
@Component
public class ProductAssembler {

    private final LinkBuilder links;

    public ProductAssembler(LinkBuilder links) {
        this.links = links;
    }

    public Resource toResource(Product product, QueryParams params) {
        Resource r = new Resource();
        Map<String, Object> p = r.getProperties();
        p.put("id", product.getId());
        p.put("name", product.getName());
        p.put("price", product.getPrice());

        Map<String, List<Link>> l = r.getLinks();
        l.put("self", List.of(new Link("self", links.product(product.getId()))));
        l.put("products", List.of(new Link("products", links.products())));
        return r;
    }
}
