package com.github.guoyyit.hypermediaresourceapijava.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Plain hypermedia JSON: {@code application/vnd.hypermedia+json}.
 * Links are a {@code links} array; related resources are under
 * {@code embedded}; resource state sits under {@code state}.
 */
public class PlainJsonMessageConverter extends AbstractHttpMessageConverter<Representable> {

    public static final MediaType PLAIN_JSON =
            MediaType.parseMediaType("application/vnd.hypermedia+json");

    private final RepresentJsonWriter writer;

    public PlainJsonMessageConverter(RepresentJsonWriter writer, ObjectMapper objectMapper) {
        super(StandardCharsets.UTF_8, PLAIN_JSON);
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
            writer.write(gen, representable, false);
        }
    }
}
