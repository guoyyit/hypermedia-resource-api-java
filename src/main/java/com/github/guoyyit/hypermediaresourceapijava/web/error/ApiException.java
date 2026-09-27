package com.github.guoyyit.hypermediaresourceapijava.web.error;

import java.util.Map;

/**
 * Base class for documented errors. Carries a machine code, a client-safe
 * message (never stack traces or internal class names) and safe navigation
 * links (rel -> href).
 */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final Map<String, String> links;

    protected ApiException(String code, String message, Map<String, String> links) {
        super(message);
        this.code = code;
        this.links = links == null ? Map.of() : Map.copyOf(links);
    }

    public String getCode() { return code; }
    public Map<String, String> getLinks() { return links; }
}
