package com.github.guoyyit.hypermediaresourceapijava.api;

import com.github.guoyyit.hypermediaresourceapijava.api.dto.CustomerRequest;
import com.github.guoyyit.hypermediaresourceapijava.domain.Customer;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.error.NotFoundException;
import com.github.guoyyit.hypermediaresourceapijava.error.PreconditionFailedException;
import com.github.guoyyit.hypermediaresourceapijava.error.PreconditionRequiredException;
import com.github.guoyyit.hypermediaresourceapijava.error.RangeNotSatisfiableException;
import com.github.guoyyit.hypermediaresourceapijava.error.UnprocessableEntityException;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.represent.ResourceList;
import com.github.guoyyit.hypermediaresourceapijava.service.ConditionalRequests;
import com.github.guoyyit.hypermediaresourceapijava.service.CustomerAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import com.github.guoyyit.hypermediaresourceapijava.service.ListAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.OrderAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.QueryParams;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import com.github.guoyyit.hypermediaresourceapijava.web.QueryParamParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.List;

/** Customer collection/aggregate routes. */
@RestController
public class CustomerController {

    private final InMemoryStore store;
    private final CustomerAssembler assembler;
    private final OrderAssembler orderAssembler;
    private final ListAssembler listAssembler;
    private final QueryParamParser parser;
    private final LinkBuilder links;
    private final ConditionalRequests conditional;

    public CustomerController(InMemoryStore store, CustomerAssembler assembler,
                              OrderAssembler orderAssembler, ListAssembler listAssembler,
                              QueryParamParser parser, LinkBuilder links,
                              ConditionalRequests conditional) {
        this.store = store;
        this.assembler = assembler;
        this.orderAssembler = orderAssembler;
        this.listAssembler = listAssembler;
        this.parser = parser;
        this.links = links;
        this.conditional = conditional;
    }

    @GetMapping("/customers")
    public Representable list(WebRequest webRequest) {
        QueryParams params = parser.parse(webRequest);
        List<Customer> all = store.findAllCustomers();
        return toPage(all, params, links.customers(),
                c -> assembler.toResource(c, params));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Representable> get(@PathVariable String id, WebRequest webRequest,
                                             @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                     required = false) String ifNoneMatch) {
        Customer customer = load(id);
        QueryParams params = parser.parse(webRequest);
        String etag = conditional.etag(customer.getId(), customer.getVersion());
        if (conditional.ifNoneMatchFails(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(assembler.toResource(customer, params));
    }

    @PostMapping("/customers")
    public ResponseEntity<Representable> create(@RequestBody CustomerRequest body,
                                                WebRequest webRequest) {
        validate(body);
        Customer created = store.withWriteLock(() -> {
            Customer customer = new Customer();
            customer.setId("c-" + System.nanoTime());
            customer.setName(body.getName());
            customer.setEmail(body.getEmail());
            customer.setVersion(0);
            Instant now = Instant.now();
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            store.saveCustomer(customer);
            return customer;
        });
        QueryParams params = parser.parse(webRequest);
        return ResponseEntity.created(java.net.URI.create(links.customer(created.getId())))
                .eTag(conditional.etag(created.getId(), created.getVersion()))
                .body(assembler.toResource(created, params));
    }

    /** Full update requiring If-Match (428 when absent, 412 when stale). */
    @PutMapping("/customers/{id}")
    public ResponseEntity<Representable> update(
            @PathVariable String id, @RequestBody CustomerRequest body, WebRequest webRequest,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        validate(body);
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match is required for conditional updates.");
        }

        Customer updated = store.withWriteLock(() -> {
            Customer existing = store.findCustomer(id)
                    .orElseThrow(() -> new NotFoundException("Customer '" + id + "' does not exist."));
            String etag = conditional.etag(existing.getId(), existing.getVersion());
            if (!conditional.ifMatchPasses(ifMatch, etag)) {
                throw new PreconditionFailedException(
                        "If-Match precondition failed; the customer was modified by another request.");
            }
            if (ifNoneMatch != null && !ifNoneMatch.isBlank()
                    && conditional.ifNoneMatchFails(ifNoneMatch, etag)) {
                throw new PreconditionFailedException("If-None-Match precondition failed.");
            }
            existing.setName(body.getName());
            existing.setEmail(body.getEmail());
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(Instant.now());
            store.saveCustomer(existing);
            return existing;
        });

        QueryParams params = parser.parse(webRequest);
        return ResponseEntity.ok()
                .eTag(conditional.etag(updated.getId(), updated.getVersion()))
                .body(assembler.toResource(updated, params));
    }

    /** Orders owned by one customer (paginated sub-collection). */
    @GetMapping("/customers/{id}/orders")
    public Representable customerOrders(@PathVariable String id, WebRequest webRequest) {
        load(id);
        QueryParams params = parser.parse(webRequest);
        List<Order> owned = store.findAllOrders().stream()
                .filter(o -> id.equals(o.getCustomerId()))
                .toList();
        Slice<Order> slice = slice(owned, params);
        return listAssembler.toList(links.customerOrders(id), params.page(), params.size(),
                slice.total(), slice.items(), o -> orderAssembler.toResource(o, params));
    }

    private void validate(CustomerRequest body) {
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new UnprocessableEntityException("Customer request must include a 'name'.");
        }
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            throw new UnprocessableEntityException("Customer request must include an 'email'.");
        }
    }

    private Customer load(String id) {
        return store.findCustomer(id)
                .orElseThrow(() -> new NotFoundException("Customer '" + id + "' does not exist."));
    }

    private <T> ResourceList toPage(List<T> all, QueryParams params, String href,
                                    java.util.function.Function<T, Resource> mapper) {
        Slice<T> slice = slice(all, params);
        return listAssembler.toList(href, params.page(), params.size(),
                slice.total(), slice.items(), mapper);
    }

    private record Slice<T>(long total, List<T> items) {
    }

    private <T> Slice<T> slice(List<T> all, QueryParams params) {
        long total = all.size();
        int totalPages = total == 0 ? 0 : (int) ((total + params.size() - 1) / params.size());
        if (total > 0 && params.page() >= totalPages) {
            throw new RangeNotSatisfiableException(
                    "Page " + params.page() + " is beyond the last page " + (totalPages - 1) + ".");
        }
        int from = params.page() * params.size();
        int to = (int) Math.min(total, from + params.size());
        return new Slice<>(total, all.subList(from, to));
    }
}
