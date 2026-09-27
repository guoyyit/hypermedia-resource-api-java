package com.github.guoyyit.hypermediaresourceapijava.web.controller;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ContentNegotiationService;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ETagService;
import com.github.guoyyit.hypermediaresourceapijava.hyper.EmbedSpec;
import com.github.guoyyit.hypermediaresourceapijava.hyper.HttpPaths;
import com.github.guoyyit.hypermediaresourceapijava.hyper.MediaTypeKind;
import com.github.guoyyit.hypermediaresourceapijava.hyper.Pagination;
import com.github.guoyyit.hypermediaresourceapijava.hyper.RepresentationRenderer;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ResourceRepresentation;
import com.github.guoyyit.hypermediaresourceapijava.service.OrderActionService;
import com.github.guoyyit.hypermediaresourceapijava.service.OrderRepresentationService;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryOrderStore;
import com.github.guoyyit.hypermediaresourceapijava.web.dto.OrderRequest;
import com.github.guoyyit.hypermediaresourceapijava.web.error.NotAcceptableException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.PreconditionFailedException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.PreconditionRequiredException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Order hypermedia API.
 *
 * Every response is a hypermedia representation chosen by content
 * negotiation; clients advance state exclusively through the links and
 * method-hinted controls returned for the current state.
 */
@RestController
@RequestMapping(HttpPaths.ORDERS)
public class OrderController {

    private final InMemoryOrderStore store;
    private final OrderRepresentationService representations;
    private final OrderActionService actions;
    private final ContentNegotiationService negotiation;
    private final RepresentationRenderer renderer;
    private final ETagService etags;
    private final HypermediaProperties properties;

    public OrderController(InMemoryOrderStore store,
                           OrderRepresentationService representations,
                           OrderActionService actions,
                           ContentNegotiationService negotiation,
                           RepresentationRenderer renderer,
                           ETagService etags,
                           HypermediaProperties properties) {
        this.store = store;
        this.representations = representations;
        this.actions = actions;
        this.negotiation = negotiation;
        this.renderer = renderer;
        this.etags = etags;
        this.properties = properties;
    }

    // ------------------------------------------------------------ collection

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String page,
                                  @RequestParam(required = false) String size,
                                  @RequestParam(required = false) String sort,
                                  @RequestParam(required = false) String embed,
                                  @RequestParam(required = false) String depth,
                                  @RequestParam(required = false) String fields,
                                  @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        MediaTypeKind kind = negotiate(accept);
        Pagination.PageRequest pr = Pagination.parsePageRequest(
                page, size,
                properties.getPagination().getDefaultSize(),
                properties.getPagination().getMaxSize(),
                properties.getPagination().getMaxPage());
        EmbedSpec spec = representations.parseEmbedSpec(
                embed, depth, fields, properties.getEmbedding().getMaxDepth());

        List<Order> all = store.findAll();
        int total = all.size();
        int lastPage = Math.max(1, (int) Math.ceil(total / (double) pr.size()));
        boolean outOfRange = pr.page() > lastPage;

        ResourceRepresentation rep = representations.orderCollectionRepresentation(
                all, pr.page(), pr.size(), sort, HttpPaths.ORDERS, "", spec);

        // Distinguishable, documented page states.
        boolean empty = total == 0 && pr.page() == 1;
        rep.getProperties().put("empty", total == 0);
        rep.getProperties().put("pageState",
                empty ? "EMPTY" : outOfRange ? "OUT_OF_RANGE" : "CONTENT");

        // Out-of-range pages carry no member resources (already the case) but
        // still expose first/last navigation; prev/next are suppressed beyond bounds.
        HttpStatus status = outOfRange ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        return ResponseEntity.status(status)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody OrderRequest request,
                                    @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        MediaTypeKind kind = negotiate(accept);
        Order order = actions.create(request == null ? null : request.customerName(),
                request == null ? null : request.items());
        String etag = etags.forOrder(order);
        ResourceRepresentation rep = representations.orderRepresentation(order,
                representations.parseEmbedSpec(null, null, null,
                        properties.getEmbedding().getMaxDepth()));
        return ResponseEntity.created(java.net.URI.create(rep.getSelfHref()))
                .eTag(etag)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    // --------------------------------------------------------------- single

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id,
                                 @RequestParam(required = false) String embed,
                                 @RequestParam(required = false) String depth,
                                 @RequestParam(required = false) String fields,
                                 @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                 @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MediaTypeKind kind = negotiate(accept);
        Order order = load(id);
        // If-None-Match: GET succeeds only when the current ETag does not match.
        if (ifNoneMatch != null && !ifNoneMatch.isBlank() && etags.matches(ifNoneMatch, order)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etags.forOrder(order))
                    .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                    .build();
        }
        EmbedSpec spec = representations.parseEmbedSpec(
                embed, depth, fields, properties.getEmbedding().getMaxDepth());
        ResourceRepresentation rep = representations.orderRepresentation(order, spec);
        return ResponseEntity.ok()
                .eTag(etags.forOrder(order))
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @RequestBody OrderRequest request,
                                    @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MediaTypeKind kind = negotiate(accept);
        Order current = load(id);

        // If-None-Match: * on PUT means "only if it does not exist".
        if (ifNoneMatch != null && etags.matches(ifNoneMatch, current)) {
            throw new PreconditionFailedException(
                    "PRECONDITION_FAILED",
                    "If-None-Match precondition failed: the resource already has that state",
                    Map.of("self", HttpPaths.order(id), "orders", HttpPaths.ORDERS));
        }
        // Conditional updates require If-Match (optimistic concurrency).
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "PRECONDITION_REQUIRED",
                    "Updating an order requires an If-Match header carrying its current ETag",
                    Map.of("self", HttpPaths.order(id),
                            "orders", HttpPaths.ORDERS,
                            "retry", HttpPaths.order(id)));
        }
        Long expectedVersion = etags.versionFrom(ifMatch);
        if (expectedVersion == null || !etags.matches(ifMatch, current)) {
            throw new PreconditionFailedException(
                    "PRECONDITION_FAILED",
                    "If-Match precondition failed: the resource state differs; refetch and retry",
                    Map.of("self", HttpPaths.order(id),
                            "orders", HttpPaths.ORDERS,
                            "retry", HttpPaths.order(id)));
        }

        Order updated = actions.update(id, expectedVersion,
                request == null ? null : request.customerName(),
                request == null ? null : request.items());
        ResourceRepresentation rep = representations.orderRepresentation(updated,
                representations.parseEmbedSpec(null, null, null,
                        properties.getEmbedding().getMaxDepth()));
        return ResponseEntity.ok()
                .eTag(etags.forOrder(updated))
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    @PostMapping("/{id}/transitions/{action}")
    public ResponseEntity<?> transition(@PathVariable String id,
                                        @PathVariable String action,
                                        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        MediaTypeKind kind = negotiate(accept);
        Order current = load(id);
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "PRECONDITION_REQUIRED",
                    "Executing a transition requires an If-Match header with the current ETag",
                    Map.of("self", HttpPaths.order(id),
                            "orders", HttpPaths.ORDERS,
                            "retry", HttpPaths.order(id)));
        }
        Long expectedVersion = etags.versionFrom(ifMatch);
        if (expectedVersion == null || !etags.matches(ifMatch, current)) {
            throw new PreconditionFailedException(
                    "PRECONDITION_FAILED",
                    "If-Match precondition failed: the resource state differs; refetch and retry",
                    Map.of("self", HttpPaths.order(id),
                            "orders", HttpPaths.ORDERS,
                            "retry", HttpPaths.order(id)));
        }

        Order updated = actions.transition(id, expectedVersion, action);
        ResourceRepresentation rep = representations.orderRepresentation(updated,
                representations.parseEmbedSpec(null, null, null,
                        properties.getEmbedding().getMaxDepth()));
        return ResponseEntity.ok()
                .eTag(etags.forOrder(updated))
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    // ----------------------------------------------------------------- items

    @GetMapping("/{id}/items")
    public ResponseEntity<?> items(@PathVariable String id,
                                   @RequestParam(required = false) String embed,
                                   @RequestParam(required = false) String depth,
                                   @RequestParam(required = false) String fields,
                                   @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        MediaTypeKind kind = negotiate(accept);
        Order order = load(id);
        EmbedSpec spec = representations.parseEmbedSpec(
                embed, depth, fields, properties.getEmbedding().getMaxDepth());
        ResourceRepresentation rep =
                representations.itemCollectionRepresentation(order, HttpPaths.ORDERS, spec);
        return ResponseEntity.ok()
                .eTag(etags.forOrder(order))
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    @GetMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> item(@PathVariable String id,
                                  @PathVariable String itemId,
                                  @RequestParam(required = false) String embed,
                                  @RequestParam(required = false) String depth,
                                  @RequestParam(required = false) String fields,
                                  @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                  @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MediaTypeKind kind = negotiate(accept);
        Order order = load(id);
        OrderItem item = order.getItems().stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ITEM_NOT_FOUND",
                        "No line item exists at the requested path",
                        Map.of("self", OrderRepresentationService.itemSelfHref(id, itemId),
                                "order", HttpPaths.order(id),
                                "items", HttpPaths.order(id) + "/items")));
        if (ifNoneMatch != null && !ifNoneMatch.isBlank() && etags.matches(ifNoneMatch, order)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etags.forOrder(order))
                    .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                    .build();
        }
        EmbedSpec spec = representations.parseEmbedSpec(
                embed, depth, fields, properties.getEmbedding().getMaxDepth());
        ResourceRepresentation rep = representations.itemRepresentation(item, spec);
        return ResponseEntity.ok()
                .eTag(etags.forOrder(order))
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }

    // -------------------------------------------------------------- helpers

    private MediaTypeKind negotiate(String accept) {
        try {
            return negotiation.select(accept);
        } catch (NotAcceptableException ex) {
            throw ex;
        }
    }

    private Order load(String id) {
        return store.findById(id).orElseThrow(() -> new ResourceNotFoundException(
                "ORDER_NOT_FOUND",
                "No order exists at /orders/" + id,
                Map.of("self", HttpPaths.order(id), "orders", HttpPaths.ORDERS, "home", "/")));
    }
}
