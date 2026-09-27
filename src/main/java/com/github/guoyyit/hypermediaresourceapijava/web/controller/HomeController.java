package com.github.guoyyit.hypermediaresourceapijava.web.controller;

import com.github.guoyyit.hypermediaresourceapijava.hyper.ContentNegotiationService;
import com.github.guoyyit.hypermediaresourceapijava.hyper.HttpPaths;
import com.github.guoyyit.hypermediaresourceapijava.hyper.Link;
import com.github.guoyyit.hypermediaresourceapijava.hyper.MediaTypeKind;
import com.github.guoyyit.hypermediaresourceapijava.hyper.RepresentationRenderer;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ResourceRepresentation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** API entry point: advertises top-level resources and the create control. */
@RestController
public class HomeController {

    private final ContentNegotiationService negotiation;
    private final RepresentationRenderer renderer;

    public HomeController(ContentNegotiationService negotiation, RepresentationRenderer renderer) {
        this.negotiation = negotiation;
        this.renderer = renderer;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        MediaTypeKind kind = negotiation.select(accept);

        ResourceRepresentation rep = new ResourceRepresentation();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", "Hypermedia Order API");
        props.put("version", "1");
        rep.setProperties(props);

        Map<String, Link> links = new TreeMap<>();
        links.put("self", Link.self("/"));
        links.put("orders", Link.get("orders", HttpPaths.ORDERS, "Order collection"));
        links.put("create", Link.action("create", HttpPaths.ORDERS, "POST", "Create a new order"));
        rep.setLinks(new LinkedHashMap<>(links));

        return ResponseEntity.ok()
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                .contentType(org.springframework.http.MediaType.parseMediaType(kind.value()))
                .body(renderer.render(rep, kind));
    }
}
