package com.github.guoyyit.hypermediaresourceapijava.web.advice;

import com.github.guoyyit.hypermediaresourceapijava.hyper.ContentNegotiationService;
import com.github.guoyyit.hypermediaresourceapijava.hyper.Link;
import com.github.guoyyit.hypermediaresourceapijava.hyper.MediaTypeKind;
import com.github.guoyyit.hypermediaresourceapijava.hyper.RepresentationRenderer;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ResourceRepresentation;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ApiException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.BadRequestException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ConflictException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.MethodNotAllowedException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.NotAcceptableException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.PreconditionFailedException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.PreconditionRequiredException;
import com.github.guoyyit.hypermediaresourceapijava.web.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Translates every failure into a navigable error resource.
 *
 * The error body is itself a hypermedia representation:
 * <ul>
 *   <li>{@code type}     - stable problem type URI;</li>
 *   <li>{@code status}   - HTTP status code;</li>
 *   <li>{@code code}     - stable machine-readable error code;</li>
 *   <li>{@code detail}   - client-safe message (never a stack trace or class name);</li>
 *   <li>{@code timestamp} - ISO-8601 generation time;</li>
 *   <li>{@code _links}   - self plus safe retry/navigation controls.</li>
 * </ul>
 * Negotiation failures are served as HAL so the contract is always readable.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final ContentNegotiationService negotiation;
    private final RepresentationRenderer renderer;

    public ApiExceptionHandler(ContentNegotiationService negotiation,
                               RepresentationRenderer renderer) {
        this.negotiation = negotiation;
        this.renderer = renderer;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex);
        return build(status, ex.getCode(), ex.getMessage(), ex.getLinks(), request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "No resource exists at the requested path",
                Map.of("home", "/", "orders", "/orders"), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "The HTTP method is not supported for this resource; inspect the resource links",
                Map.of("self", safeSelf(request), "home", "/", "orders", "/orders"),
                request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotAcceptableException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE, "NEGOTIATION_FAILED",
                "No supported representation matches the Accept header; offered: "
                        + MediaTypeKind.HAL.value() + ", " + MediaTypeKind.VND.value(),
                Map.of("home", "/", "orders", "/orders"), request, MediaTypeKind.HAL);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body was missing, unreadable or failed validation",
                Map.of("self", safeSelf(request), "orders", "/orders"), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Deliberately generic: internal details must never leak to clients.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed due to an internal error",
                Map.of("self", safeSelf(request), "home", "/", "orders", "/orders"),
                request, null);
    }

    private HttpStatus statusFor(ApiException ex) {
        if (ex instanceof ResourceNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof BadRequestException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof ConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof PreconditionFailedException) {
            return HttpStatus.PRECONDITION_FAILED;
        }
        if (ex instanceof PreconditionRequiredException) {
            return HttpStatus.PRECONDITION_REQUIRED;
        }
        if (ex instanceof NotAcceptableException) {
            return HttpStatus.NOT_ACCEPTABLE;
        }
        if (ex instanceof MethodNotAllowedException) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String detail,
                                                      Map<String, String> suppliedLinks,
                                                      HttpServletRequest request,
                                                      MediaTypeKind forcedKind) {
        MediaTypeKind kind = forcedKind;
        if (kind == null) {
            try {
                kind = negotiation.select(request.getHeader(HttpHeaders.ACCEPT));
            } catch (NotAcceptableException e) {
                kind = MediaTypeKind.HAL;
            }
        }

        ResourceRepresentation rep = new ResourceRepresentation();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", "https://hypermedia.example/problems/" + code.toLowerCase());
        props.put("status", status.value());
        props.put("code", code);
        props.put("detail", detail);
        props.put("timestamp", Instant.now().toString());
        rep.setProperties(props);

        Map<String, Link> links = new TreeMap<>();
        links.put("self", Link.self(safeSelf(request)));
        suppliedLinks.forEach((rel, href) -> {
            if (!rel.equals("self")) {
                links.put(rel, Link.get(rel, href, null));
            }
        });
        rep.setLinks(new LinkedHashMap<>(links));

        Map<String, Object> body = renderer.render(rep, kind);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(kind.value()));
        headers.set(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        return new ResponseEntity<>(body, headers, status);
    }

    private String safeSelf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        // Strip anything that is not a path (defensive; no query/host echo).
        return URI.create(uri).getPath();
    }
}
