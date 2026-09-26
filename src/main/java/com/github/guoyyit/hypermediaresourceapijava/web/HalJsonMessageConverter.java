package com.github.guoyyit.hypermediaresourceapijava.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HAL-style JSON: {@code application/prs.hal-custom+json}.
 * Links under {@code _links}, embeds under {@code _embedded}. This is the
 * service's default representation when no Accept header is sent.
 */
public class HalJsonMessageConverter extends AbstractHttpMessageConverter<Representable> {

    public static final MediaType HAL_JSON =
            MediaType.parseMediaType("application/prs.hal-custom+json");

    private final RepresentJsonWriter writer;

    public HalJsonMessageConverter(RepresentJsonWriter writer, ObjectMapper objectMapper) {
        super(StandardCharsets.UTF_8, HAL_JSON, MediaType.APPLICATION_JSON);
        this.writer = writer;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Representable.class.isAssignableFrom(clazz);
    }

    @Override
    protected Representable readInternal(Class<? extends Representable> clazz,
                                         HttpInputMessage inputMessage)
            throws HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException("Representations are output-only.", inputMessage);
    }

    @Override
    protected void writeInternal(Representable representable, HttpOutputMessage outputMessage)
            throws IOException {
        try (com.fasterxml.jackson.core.JsonGenerator gen =
                     new ObjectMapper().getFactory().createGenerator(outputMessage.getBody())) {
            writer.write(gen, representable, true);
        }
    }
}
