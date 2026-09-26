package com.github.guoyyit.hypermediaresourceapijava.api;

import com.github.guoyyit.hypermediaresourceapijava.api.dto.CreateOrderRequest;
import com.github.guoyyit.hypermediaresourceapijava.api.dto.TransitionRequest;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.error.NotFoundException;
import com.github.guoyyit.hypermediaresourceapijava.error.RangeNotSatisfiableException;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.represent.ResourceList;
import com.github.guoyyit.hypermediaresourceapijava.service.ConditionalRequests;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import com.github.guoyyit.hypermediaresourceapijava.service.ListAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.OrderAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.OrderStateService;
import com.github.guoyyit.hypermediaresourceapijava.service.QueryParams;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import com.github.guoyyit.hypermediaresourceapijava.web.QueryParamParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

/** Order collection/aggregate routes and state transitions. */
@RestController
public class OrderController {

    private final InMemoryStore store;
    private final OrderAssembler assembler;
    private final ListAssembler listAssembler;
    private final QueryParamParser parser;
    private final LinkBuilder links;
    private final ConditionalRequests conditional;
    private final OrderStateService stateService;

    public OrderController(InMemoryStore store, OrderAssembler assembler,
                           ListAssembler listAssembler, QueryParamParser parser,
                           LinkBuilder links, ConditionalRequests conditional,
                           OrderStateService stateService) {
        this.store = store;
        this.assembler = assembler;
        this.listAssembler = listAssembler;
        this.parser = parser;
        this.links = links;
        this.conditional = conditional;
        this.stateService = stateService;
    }

    @GetMapping("/orders")
    public Representable list(WebRequest webRequest) {
        QueryParams params = parser.parse(webRequest);
        List<Order> all = store.findAllOrders();
        Slice slice = slice(all, params);
        return listAssembler.toList(links.orders(), params.page(), params.size(),
                slice.total(), slice.items(), o -> assembler.toResource(o, params));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Representable> get(@PathVariable String id, WebRequest webRequest,
                                             @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                     required = false) String ifNoneMatch) {
        Order order = load(id);
        QueryParams params = parser.parse(webRequest);
        String etag = conditional.etag(order.getId(), order.getVersion());
        if (conditional.ifNoneMatchFails(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(assembler.toResource(order, params));
    }

    /** Create an order. Idempotency-Key replays the first result. */
    @PostMapping("/orders")
    public ResponseEntity<Representable> create(@RequestBody(required = false) CreateOrderRequest body,
                                                WebRequest webRequest,
                                                @RequestHeader(value = "Idempotency-Key",
                                                        required = false) String idempotencyKey) {
        OrderStateService.ChangeResult result = stateService.create(body, idempotencyKey);
        Order order = load(result.orderId());
        QueryParams params = parser.parse(webRequest);
        ResponseEntity.BodyBuilder builder = result.status() == 201
                ? ResponseEntity.created(java.net.URI.create(links.order(order.getId())))
                : ResponseEntity.ok();
        return builder
                .eTag(conditional.etag(order.getId(), order.getVersion()))
                .body(assembler.toResource(order, params));
    }

    /**
     * Execute a transition: POST /orders/{id}/transitions {"action":"pay"}.
     * Optional If-Match guards optimistic concurrency (412 on mismatch).
     * A repeated Idempotency-Key returns the first outcome without changing
     * state. Illegal actions for the current state yield 409 and expose only
     * currently legal transitions on the linked order resource.
     */
    @PostMapping("/orders/{id}/transitions")
    public ResponseEntity<Representable> transition(
            @PathVariable String id,
            @RequestBody(required = false) TransitionRequest body,
            WebRequest webRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        String action = body == null ? null : body.getAction();
        OrderStateService.ChangeResult result =
                stateService.transition(id, action, idempotencyKey, ifMatch);
        Order order = load(result.orderId());
        QueryParams params = parser.parse(webRequest);
        return ResponseEntity.ok()
                .eTag(conditional.etag(order.getId(), order.getVersion()))
                .body(assembler.toResource(order, params));
    }

    private Order load(String id) {
        return store.findOrder(id)
                .orElseThrow(() -> new NotFoundException("Order '" + id + "' does not exist."));
    }

    private record Slice(long total, List<Order> items) {
    }

    private Slice slice(List<Order> all, QueryParams params) {
        long total = all.size();
        int totalPages = total == 0 ? 0 : (int) ((total + params.size() - 1) / params.size());
        if (total > 0 && params.page() >= totalPages) {
            throw new RangeNotSatisfiableException(
                    "Page " + params.page() + " is beyond the last page " + (totalPages - 1) + ".");
        }
        int from = params.page() * params.size();
        int to = (int) Math.min(total, from + params.size());
        return new Slice(total, all.subList(from, to));
    }
}
