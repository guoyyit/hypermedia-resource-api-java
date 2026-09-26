package com.github.guoyyit.hypermediaresourceapijava.error;

import com.github.guoyyit.hypermediaresourceapijava.represent.ErrorResource;
import com.github.guoyyit.hypermediaresourceapijava.service.LinkBuilder;
import com.github.guoyyit.hypermediaresourceapijava.web.HalJsonMessageConverter;
import com.github.guoyyit.hypermediaresourceapijava.web.PlainJsonMessageConverter;
import com.github.guoyyit.hypermediaresourceapijava.web.RepresentJsonWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every failure into the documented navigable error contract.
 *
 * <p>Error bodies are written manually through {@link RepresentJsonWriter} so
 * that a content-negotiation failure itself still produces a
 * protocol-consistent body: when the client cannot accept either offered
 * hypermedia type, 406 is served as HAL JSON with an
 * {@code content-negotiation-failed} code and {@code availableRepresentations}
 * details.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final ErrorFactory errors;
    private final RepresentJsonWriter writer;
    private final ObjectMapper mapper;
    private final LinkBuilder linkBuilder;

    public ApiExceptionHandler(ErrorFactory errors, RepresentJsonWriter writer,
                               ObjectMapper mapper, LinkBuilder linkBuilder) {
        this.errors = errors;
        this.writer = writer;
        this.mapper = mapper;
        this.linkBuilder = linkBuilder;
    }

    @ExceptionHandler(NotFoundException.class)
    public void handleNotFound(NotFoundException ex, HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        writeError(response, request, HttpStatus.NOT_FOUND, "resource-not-found",
                ex.getMessage(), true, null, null);
    }

    @ExceptionHandler(ConflictException.class)
    public void handleConflict(ConflictException ex, HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        String current = currentHref(request);
        writeError(response, request, HttpStatus.CONFLICT, "illegal-transition",
                ex.getMessage(), true, null, current);
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public void handlePrecondition(PreconditionFailedException ex, HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        String current = currentHref(request);
        writeError(response, request, HttpStatus.PRECONDITION_FAILED, "precondition-failed",
                ex.getMessage(), true, null, current);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public void handlePreconditionRequired(PreconditionRequiredException ex,
                                           HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        writeError(response, request, HttpStatus.PRECONDITION_REQUIRED,
                "precondition-required", ex.getMessage(), false, null, currentHref(request));
    }

    @ExceptionHandler(RangeNotSatisfiableException.class)
    public void handleRange(RangeNotSatisfiableException ex, HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        Map<String, Object> details = new LinkedHashMap<>();
        writeError(response, request, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "page-out-of-range", ex.getMessage(), false, details, null);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public void handleUnprocessable(UnprocessableEntityException ex, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        writeError(response, request, HttpStatus.UNPROCESSABLE_ENTITY, "invalid-request",
                ex.getMessage(), false, null, null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public void handleBadRequest(Exception ex, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        writeError(response, request, HttpStatus.BAD_REQUEST, "bad-request",
                "The request could not be understood.", false, null, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("availableRepresentations", java.util.List.of(
                HalJsonMessageConverter.HAL_JSON.toString(),
                PlainJsonMessageConverter.PLAIN_JSON.toString(),
                MediaType.APPLICATION_JSON_VALUE));
        writeError(response, request, HttpStatus.NOT_ACCEPTABLE, "content-negotiation-failed",
                "None of the requested media types can be produced.", false, details, null,
                HalJsonMessageConverter.HAL_JSON);
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(Exception ex, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        // Deliberately generic: never leak internal implementation details.
        writeError(response, request, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred while processing the request.", false, null, null);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            HttpStatus status, String code, String message,
                            boolean retryable, Map<String, Object> details,
                            String currentHref) throws IOException {
        writeError(response, request, status, code, message, retryable, details, currentHref,
                selectMediaType(request));
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            HttpStatus status, String code, String message,
                            boolean retryable, Map<String, Object> details,
                            String currentHref, MediaType mediaType) throws IOException {
        ErrorResource body = errors.build(status.value(), code, message, retryable,
                details, safeSelf(request), currentHref);

        response.setStatus(status.value());
        response.setContentType(mediaType.toString());
        response.setCharacterEncoding("UTF-8");
        boolean hal = !PlainJsonMessageConverter.PLAIN_JSON.includes(mediaType);
        try (JsonGenerator gen = mapper.getFactory().createGenerator(response.getWriter())) {
            writer.write(gen, body, hal);
        }
    }

    private MediaType selectMediaType(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(PlainJsonMessageConverter.PLAIN_JSON.getType())
                && accept.contains(PlainJsonMessageConverter.PLAIN_JSON.getSubtype())) {
            return PlainJsonMessageConverter.PLAIN_JSON;
        }
        return HalJsonMessageConverter.HAL_JSON;
    }

    private String safeSelf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri = uri + "?" + request.getQueryString();
        }
        return uri;
    }

    private String currentHref(HttpServletRequest request) {
        // For /orders/{id}/transitions the safe GET target is the order itself;
        // for /orders/{id} it is the request URI. Other resources fall back to
        // the request URI.
        String uri = request.getRequestURI();
        if (uri.endsWith("/transitions")) {
            return uri.substring(0, uri.length() - "/transitions".length());
        }
        return uri;
    }
}
