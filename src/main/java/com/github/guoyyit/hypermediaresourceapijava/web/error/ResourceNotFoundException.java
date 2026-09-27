package com.github.guoyyit.hypermediaresourceapijava.web.error;

import java.util.Map;

/** Documented API error carrying an HTTP status, error code and safe links. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message, Map<String, String> links) {
        super(code, message, links);
    }
}
