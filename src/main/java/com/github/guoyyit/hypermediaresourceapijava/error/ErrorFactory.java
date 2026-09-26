package com.github.guoyyit.hypermediaresourceapijava.error;

import com.github.guoyyit.hypermediaresourceapijava.represent.ErrorResource;
import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the single, documented error representation.
 *
 * <p>Shape (both media types share the same {@code error} object):
 * <pre>
 * "error": {
 *   "status":      HTTP status code (integer),
 *   "code":        stable machine-readable code (kebab-case string),
 *   "message":     safe human-readable explanation,
 *   "retryable":   whether the client may safely retry the same request,
 *   "details":     optional deterministic detail fields (no internals)
 * }
 * </pre>
 * Links are safe navigations only:
 * <ul>
 *   <li>{@code self} — the request URI that failed (echoed by the handler);</li>
 *   <li>{@code home} — API entry point, always;</li>
 *   <li>{@code retry} — present exactly when {@code retryable} is true, it is
 *       a GET link to the current resource state (never an unsafe method);</li>
 *   <li>{@code current} — present on conflict/precondition failures, linking
 *       to the unmodified aggregate so the client can refetch the latest
 *       state and retry.</li>
 * </ul>
 * Stack traces, exception class names and persistence details are never
 * emitted.
 */
@Component
public class ErrorFactory {

    private final LinkBuilder links;

    public ErrorFactory(LinkBuilder links) {
        this.links = links;
    }

    public ErrorResource build(int status, String code, String message,
                               boolean retryable, Map<String, Object> details,
                               String selfHref, String currentHref) {
        ErrorResource r = new ErrorResource();
        Map<String, Object> error = r.getError();
        Map<String, Object> orderedDetails = new LinkedHashMap<>();
        if (details != null) {
            orderedDetails.putAll(details);
        }

        error.put("status", status);
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        if (!orderedDetails.isEmpty()) {
            error.put("details", orderedDetails);
        }

        Map<String, List<Link>> l = r.getLinks();
        l.put("self", List.of(new Link("self", selfHref == null ? links.home() : selfHref)));
        l.put("home", List.of(new Link("home", links.home())));
        if (retryable && currentHref != null) {
            l.put("retry", List.of(new Link("retry", currentHref)));
        }
        if (currentHref != null) {
            l.put("current", List.of(new Link("current", currentHref)));
        }
        return r;
    }
}
