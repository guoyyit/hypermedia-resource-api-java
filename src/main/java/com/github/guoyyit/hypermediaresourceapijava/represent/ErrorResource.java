package com.github.guoyyit.hypermediaresourceapijava.represent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Navigable error representation. Always carries a self link and only safe
 * links (retry / current-state retrieval). Never includes stack traces or
 * internal class names.
 */
public final class ErrorResource implements Representable {

    private final Map<String, Object> error = new LinkedHashMap<>();
    private final Map<String, List<Link>> links = new LinkedHashMap<>();

    public Map<String, Object> getError() { return error; }
    public Map<String, List<Link>> getLinks() { return links; }
}
