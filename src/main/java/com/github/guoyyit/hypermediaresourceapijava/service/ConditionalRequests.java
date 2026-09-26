package com.github.guoyyit.hypermediaresourceapijava.service;

import org.springframework.stereotype.Component;

/**
 * ETag computation and precondition evaluation (RFC 7232 semantics).
 *
 * <p>Each mutable aggregate carries a monotonically increasing optimistic
 * {@code version}; the ETag is a strong validator derived from
 * {@code "<id>:v<version>"}. Immutable products use a stable {@code p<id>}
 * validator.
 */
@Component
public class ConditionalRequests {

    /** Strong ETag for an aggregate id + optimistic version. */
    public String etag(String id, long version) {
        return "\"" + id + ":v" + version + "\"";
    }

    /** Strong ETag for an immutable product. */
    public String productEtag(String id) {
        return "\"" + id + ":p1\"";
    }

    /**
     * Evaluate If-None-Match against the current ETag.
     *
     * @return true when the precondition FAILS (i.e. the current representation
     *         matches one of the listed validators, or "*" was supplied)
     */
    public boolean ifNoneMatchFails(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        return matches(ifNoneMatch, currentEtag);
    }

    /**
     * Evaluate If-Match against the current ETag.
     *
     * @return true when the precondition PASSES (at least one listed validator
     *         matches, or "*" was supplied against an existing resource)
     */
    public boolean ifMatchPasses(String ifMatch, String currentEtag) {
        if (ifMatch == null || ifMatch.isBlank()) {
            // No precondition header => the update is unconditional.
            return true;
        }
        return matches(ifMatch, currentEtag);
    }

    private boolean matches(String headerValue, String currentEtag) {
        String raw = headerValue.trim();
        if ("*".equals(raw)) {
            return true;
        }
        for (String token : raw.split(",")) {
            String candidate = token.trim();
            // Weak validators never match strong comparison performed for
            // If-Match; our own ETags are always strong.
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2).trim();
            }
            if (candidate.equals(currentEtag)) {
                return true;
            }
        }
        return false;
    }
}
