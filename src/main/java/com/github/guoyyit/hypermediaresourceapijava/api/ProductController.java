package com.github.guoyyit.hypermediaresourceapijava.api;

import com.github.guoyyit.hypermediaresourceapijava.error.NotFoundException;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import com.github.guoyyit.hypermediaresourceapijava.represent.ResourceList;
import com.github.guoyyit.hypermediaresourceapijava.service.ConditionalRequests;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import com.github.guoyyit.hypermediaresourceapijava.service.ListAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.ProductAssembler;
import com.github.guoyyit.hypermediaresourceapijava.service.QueryParams;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryStore;
import com.github.guoyyit.hypermediaresourceapijava.web.QueryParamParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

/** Product catalog routes (read-only, immutable resources). */
@RestController
public class ProductController {

    private final InMemoryStore store;
    private final ProductAssembler assembler;
    private final ListAssembler listAssembler;
    private final QueryParamParser parser;
    private final LinkBuilder links;
    private final ConditionalRequests conditional;

    public ProductController(InMemoryStore store, ProductAssembler assembler,
                             ListAssembler listAssembler, QueryParamParser parser,
                             LinkBuilder links, ConditionalRequests conditional) {
        this.store = store;
        this.assembler = assembler;
        this.listAssembler = listAssembler;
        this.parser = parser;
        this.links = links;
        this.conditional = conditional;
    }

    @GetMapping("/products")
    public Representable list(WebRequest webRequest, HttpServletRequest request) {
        QueryParams params = parser.parse(webRequest);
        List<com.github.guoyyit.hypermediaresourceapijava.domain.Product> all = store.findAllProducts();
        return paginate(all, params, links.products());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Representable> get(@PathVariable String id, WebRequest webRequest,
                                             @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                     required = false) String ifNoneMatch) {
        var product = store.findProduct(id)
                .orElseThrow(() -> new NotFoundException("Product '" + id + "' does not exist."));
        QueryParams params = parser.parse(webRequest);

        String etag = conditional.productEtag(product.getId());
        if (conditional.ifNoneMatchFails(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .body(assembler.toResource(product, params));
    }

    private <T> ResourceList paginate(List<T> all, QueryParams params, String href) {
        long total = all.size();
        int totalPages = total == 0 ? 0 : (int) ((total + params.size() - 1) / params.size());
        if (total > 0 && params.page() >= totalPages) {
            throw new com.github.guoyyit.hypermediaresourceapijava.error
                    .RangeNotSatisfiableException(
                    "Page " + params.page() + " is beyond the last page " + (totalPages - 1) + ".");
        }
        int from = params.page() * params.size();
        int to = (int) Math.min(total, from + params.size());
        List<T> slice = all.subList(from, to);
        return listAssembler.toList(href, params.page(), params.size(), total, slice,
                t -> assembler.toResource((com.github.guoyyit.hypermediaresourceapijava.domain.Product) t,
                        params));
    }
}
