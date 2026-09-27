package com.github.guoyyit.hypermediaresourceapijava.hyper;

import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import com.github.guoyyit.hypermediaresourceapijava.web.error.NotAcceptableException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Selects a {@link MediaTypeKind} from the Accept header using RFC 7231
 * precedence (q-values, then specificity, then header order). A blank Accept
 * header or a sole wildcard means "any" and yields the configured default.
 *
 * Explicitly unsupported preferences raise {@link NotAcceptableException},
 * which is rendered as the documented negotiation-failure contract.
 */
@Component
public class ContentNegotiationService {

    private final HypermediaProperties properties;

    public ContentNegotiationService(HypermediaProperties properties) {
        this.properties = properties;
    }

    public MediaTypeKind select(String acceptHeader) {
        MediaTypeKind configuredDefault = parseConfiguredDefault();

        if (acceptHeader == null || acceptHeader.isBlank()) {
            return configuredDefault;
        }

        List<Token> tokens = parse(acceptHeader);
        if (tokens.isEmpty()) {
            return configuredDefault;
        }

        boolean hasWildcard = false;
        MediaTypeKind best = null;
        double bestQ = -1;
        int bestSpecificity = -1;
        int bestOrder = Integer.MAX_VALUE;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.q == 0.0d) {
                continue;
            }
            MediaTypeKind match = matchKind(token);
            if (match != null) {
                int specificity = token.specificity();
                // Prefer: higher q, then more specific media range, then earlier order.
                if (token.q > bestQ
                        || (token.q == bestQ && specificity > bestSpecificity)
                        || (token.q == bestQ && specificity == bestSpecificity && i < bestOrder)) {
                    best = match;
                    bestQ = token.q;
                    bestSpecificity = specificity;
                    bestOrder = i;
                }
            }
            if ("*/*".equals(token.raw) || "application/*".equals(token.raw)) {
                hasWildcard = true;
            }
        }

        if (best != null) {
            return best;
        }
        if (hasWildcard) {
            return configuredDefault;
        }
        throw new NotAcceptableException(
                "NEGOTIATION_FAILED",
                "No supported representation matches the Accept header; offered: "
                        + MediaTypeKind.HAL.value() + ", " + MediaTypeKind.VND.value(),
                Map.of("self", "/error", "orders", "/orders"));
    }

    private MediaTypeKind parseConfiguredDefault() {
        String value = properties.getRepresentation().getDefaultMediaType();
        for (MediaTypeKind kind : MediaTypeKind.values()) {
            if (kind.value().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return MediaTypeKind.HAL;
    }

    private MediaTypeKind matchKind(Token token) {
        String raw = token.raw;
        if ("*/*".equals(raw) || "application/*".equals(raw)) {
            return parseConfiguredDefault();
        }
        for (MediaTypeKind kind : MediaTypeKind.values()) {
            if (kind.value().equalsIgnoreCase(raw) || "application/json".equalsIgnoreCase(raw)) {
                // application/json is treated as the HAL JSON shape (default JSON).
                return "application/json".equalsIgnoreCase(raw) ? MediaTypeKind.HAL : kind;
            }
        }
        return null;
    }

    private List<Token> parse(String header) {
        List<Token> result = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pieces = part.trim().split(";");
            if (pieces.length == 0 || pieces[0].isBlank()) {
                continue;
            }
            double q = 1.0d;
            for (int i = 1; i < pieces.length; i++) {
                String param = pieces[i].trim();
                if (param.startsWith("q=") || param.startsWith("Q=")) {
                    try {
                        q = Double.parseDouble(param.substring(2));
                    } catch (NumberFormatException ignored) {
                        q = 0.0d;
                    }
                }
            }
            result.add(new Token(pieces[0].trim().toLowerCase(), q));
        }
        // Stable ordering: preserve header order; tie-breaking by index happens above.
        return result;
    }

    private record Token(String raw, double q) {
        int specificity() {
            return switch (raw) {
                case "*/*" -> 0;
                case "application/*" -> 1;
                default -> 2;
            };
        }
    }
}
