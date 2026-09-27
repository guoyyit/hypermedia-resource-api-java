package com.github.guoyyit.hypermediaresourceapijava.hyper;

import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import org.springframework.stereotype.Component;

/**
 * Opaque weak ETags derived from {@code orderId:version}. Weak validators are
 * used because the two supported JSON layouts are byte-different but
 * semantically equivalent, and ETags identify state/version rather than an
 * exact serialization.
 */
@Component
public class ETagService {

    public String forOrder(Order order) {
        return "W/\"" + order.getId() + ":" + order.getVersion() + "\"";
    }

    /** Extract the version encoded in an ETag, or null when malformed. */
    public Long versionFrom(String etag) {
        if (etag == null) {
            return null;
        }
        String value = etag.trim();
        if (value.startsWith("W/") || value.startsWith("w/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            return Long.parseLong(value.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Conditional-header matching. Supports a single ETag or a comma-separated
     * list (as in If-Match). "*" matches any existing resource.
     */
    public boolean matches(String headerValue, Order order) {
        if (headerValue == null || order == null) {
            return false;
        }
        String current = forOrder(order);
        for (String candidate : headerValue.split(",")) {
            String token = candidate.trim();
            if ("*".equals(token)) {
                return true;
            }
            if (token.equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }
}
