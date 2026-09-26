package com.github.guoyyit.hypermediaresourceapijava.web;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.error.UnprocessableEntityException;
import com.github.guoyyit.hypermediaresourceapijava.service.QueryParams;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parses and normalises list/embed query parameters.
 *
 * <p>Supported parameters:
 * <ul>
 *   <li>{@code embed} — comma-separated relation paths, repeatable. Order of
 *       first occurrence is preserved, duplicates removed;</li>
 *   <li>{@code embed-depth} — positive integer, clamped to
 *       {@code hypermedia.embed-max-depth} (default 3). Values above the cap
 *       are silently reduced to the cap;</li>
 *   <li>{@code fields} — comma-separated top-level property names,
 *       repeatable, order preserved, duplicates removed. Absence means all
 *       fields;</li>
 *   <li>{@code page} — zero-based non-negative integer (default 0). A negative
 *       or non-numeric value yields 422 {@code invalid-query-parameter};</li>
 *   <li>{@code size} — positive integer, default
 *       {@code hypermedia.default-page-size}, capped at
 *       {@code hypermedia.max-page-size}. Values above the cap are silently
 *       reduced to the cap; zero/negative/non-numeric yields 422.</li>
 * </ul>
 */
@Component
public class QueryParamParser {

    private final HypermediaProperties properties;

    public QueryParamParser(HypermediaProperties properties) {
        this.properties = properties;
    }

    public QueryParams parse(WebRequest request) {
        List<String> embed = splitPaths(request.getParameterValues("embed"));
        List<String> fields = splitPaths(request.getParameterValues("fields"));

        int depth = properties.getEmbedMaxDepth();
        String depthParam = request.getParameter("embed-depth");
        if (depthParam != null) {
            int requested = parseInt("embed-depth", depthParam);
            if (requested < 1) {
                throw new UnprocessableEntityException("'embed-depth' must be a positive integer.");
            }
            depth = Math.min(requested, properties.getEmbedMaxDepth());
        }

        int page = 0;
        String pageParam = request.getParameter("page");
        if (pageParam != null) {
            page = parseInt("page", pageParam);
            if (page < 0) {
                throw new UnprocessableEntityException("'page' must be a non-negative integer (0-based).");
            }
        }

        int size = properties.getDefaultPageSize();
        String sizeParam = request.getParameter("size");
        if (sizeParam != null) {
            size = parseInt("size", sizeParam);
            if (size <= 0) {
                throw new UnprocessableEntityException("'size' must be a positive integer.");
            }
            size = Math.min(size, properties.getMaxPageSize());
        }

        return new QueryParams(embed, depth, fields.isEmpty() ? null : fields, page, size);
    }

    private List<String> splitPaths(String[] values) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                for (String part : value.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        ordered.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    private int parseInt(String name, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "'" + name + "' must be an integer but was '" + raw + "'.");
        }
    }
}
