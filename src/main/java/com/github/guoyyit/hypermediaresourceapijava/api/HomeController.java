package com.github.guoyyit.hypermediaresourceapijava.api;

import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API entry point. Clients bootstrap navigation from {@code GET /}: every
 * other route is discovered via links, never hard-coded.
 *
 * <p>Links (documented order): {@code self}, {@code customers},
 * {@code customer-create}, {@code orders}, {@code order-create},
 * {@code products}.
 */
@RestController
public class HomeController {

    private final LinkBuilder links;

    public HomeController(LinkBuilder links) {
        this.links = links;
    }

    @GetMapping("/")
    public Representable home() {
        Resource r = new Resource();
        Map<String, List<Link>> l = r.getLinks();
        l.put("self", List.of(new Link("self", links.home())));
        l.put("customers", List.of(new Link("customers", links.customers())));
        l.put("customer-create", List.of(new Link("customer-create", links.customers(),
                Map.of("method", "POST", "title", "Create a new customer"))));
        l.put("orders", List.of(new Link("orders", links.orders())));
        l.put("order-create", List.of(new Link("order-create", links.orders(),
                Map.of("method", "POST", "title", "Create a new order"))));
        l.put("products", List.of(new Link("products", links.products())));
        return r;
    }
}
