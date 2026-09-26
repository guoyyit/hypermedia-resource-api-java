package com.github.guoyyit.hypermediaresourceapijava.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.hypermediaresourceapijava.config.HypermediaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web configuration.
 *
 * <p>Content negotiation:
 * <ul>
 *   <li>driven by the {@code Accept} header only (path extension and
 *       {@code ?format=} parameters are disabled);</li>
 *   <li>a request without an Accept header, or {@code *}{@code /*}, receives
 *       HAL JSON {@code application/prs.hal-custom+json};</li>
 *   <li>acceptable media types: the HAL type, the plain type, and
 *       {@code application/json} (served as HAL for compatibility);</li>
 *   <li>any other Accept preference (e.g. {@code application/xml}) cannot be
 *       satisfied: the framework raises HTTP 406, translated by
 *       {@code ApiExceptionHandler} into the documented negotiation-failure
 *       error contract.</li>
 * </ul>
 * The two hypermedia converters are registered before the default Jackson
 * converter, which is restricted to non-Representable body types (request
 * DTOs) to avoid ambiguous serialisation.
 */
@Configuration
@EnableConfigurationProperties(HypermediaProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;
    private final RepresentJsonWriter representJsonWriter;

    public WebMvcConfig(ObjectMapper objectMapper, RepresentJsonWriter representJsonWriter) {
        this.objectMapper = objectMapper;
        this.representJsonWriter = representJsonWriter;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Prepend both hypermedia converters so Representable responses are
        // always claimed by them (in negotiated order), regardless of the
        // default Jackson converter that Boot registers.
        converters.add(0, new PlainJsonMessageConverter(representJsonWriter, objectMapper));
        converters.add(0, new HalJsonMessageConverter(representJsonWriter, objectMapper));

        // Restrict the default Jackson converter to request-body media types;
        // response bodies are Representables handled by the converters above.
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setSupportedMediaTypes(List.of(
                        MediaType.APPLICATION_JSON,
                        new MediaType("application", "*+json")));
            }
        }
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .favorPathExtension(false)
                .ignoreAcceptHeader(false)
                .defaultContentType(HalJsonMessageConverter.HAL_JSON)
                .mediaType("json", org.springframework.http.MediaType.APPLICATION_JSON)
                .mediaType("haljson", HalJsonMessageConverter.HAL_JSON)
                .mediaType("hypermedia", PlainJsonMessageConverter.PLAIN_JSON);
    }
}
