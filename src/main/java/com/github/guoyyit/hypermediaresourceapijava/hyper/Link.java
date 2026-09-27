package com.github.guoyyit.hypermediaresourceapijava.hyper;

/**
 * An RFC 8288-style link. {@code method} (a hypermedia control hint, e.g.
 * "PUT"/"POST") is emitted only for non-GET controls.
 */
public record Link(String rel, String href, String method, String title) {

    public static Link self(String href) {
        return new Link("self", href, null, null);
    }

    public static Link get(String rel, String href, String title) {
        return new Link(rel, href, null, title);
    }

    public static Link action(String rel, String href, String method, String title) {
        return new Link(rel, href, method, title);
    }
}
