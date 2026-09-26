package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conditional reads/updates and concurrency:
 * <ul>
 *   <li>ETag + If-None-Match produces 304 on match and 200 on mismatch;</li>
 *   <li>If-Match stale -> 412 with a navigable error, correct -> success;</li>
 *   <li>PUT without If-Match -> 428;</li>
 *   <li>same Idempotency-Key replays without a second state change;</li>
 *   <li>same key with a different body -> 422;</li>
 *   <li>concurrent identical legal transitions: exactly one succeeds, the
 *       loser gets 409, final version increments exactly once;</li>
 *   <li>failed transition leaves no intermediate state (version unchanged).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConditionalConcurrencyIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private String createFreshOrder() {
        ResponseEntity<String> response = rest.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"customerId":"c-1","items":[{"productId":"p-1","quantity":1}]}
                        """, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return Json.parse(json, response.getBody()).path("id").asText();
    }

    private JsonNode getOrder(String id) {
        return Json.parse(json, rest.getForEntity("/orders/" + id, String.class).getBody());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> transition(String id, String action,
                                              String idempotencyKey, String ifMatch) {
        HttpHeaders headers = jsonHeaders();
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        if (ifMatch != null) {
            headers.set(HttpHeaders.IF_MATCH, ifMatch);
        }
        return rest.exchange("/orders/" + id + "/transitions", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"" + action + "\"}", headers), String.class);
    }

    @Test
    void etagConditionalReadReturns304Then200AfterChange() {
        String id = createFreshOrder();
        ResponseEntity<String> first = rest.getForEntity("/orders/" + id, String.class);
        String etag = first.getHeaders().getETag();
        assertThat(etag).isNotBlank();

        HttpHeaders matchHeaders = new HttpHeaders();
        matchHeaders.set(HttpHeaders.IF_NONE_MATCH, etag);
        ResponseEntity<String> notModified = rest.exchange("/orders/" + id, HttpMethod.GET,
                new HttpEntity<>(matchHeaders), String.class);
        assertThat(notModified.getStatusCode().value()).isEqualTo(304);

        transition(id, "pay", null, null);
        ResponseEntity<String> changed = rest.exchange("/orders/" + id, HttpMethod.GET,
                new HttpEntity<>(matchHeaders), String.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void staleIfMatchOnTransitionIs412AndDoesNotChangeState() {
        String id = createFreshOrder();
        long versionBefore = getOrder(id).path("version").asLong();

        ResponseEntity<String> response = transition(id, "pay", null, "\"" + id + ":v999\"");
        assertThat(response.getStatusCode().value()).isEqualTo(412);
        JsonNode error = Json.parse(json, response.getBody());
        assertThat(error.path("error").path("code").asText()).isEqualTo("precondition-failed");
        assertThat(error.path("_links").path("current").path("href").asText())
                .endsWith("/orders/" + id);

        JsonNode after = getOrder(id);
        assertThat(after.path("state").asText()).isEqualTo("created");
        assertThat(after.path("version").asLong()).isEqualTo(versionBefore);
    }

    @Test
    void currentIfMatchAllowsTransition() {
        String id = createFreshOrder();
        String etag = "\"" + id + ":v0\"";
        ResponseEntity<String> response = transition(id, "pay", null, etag);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(getOrder(id).path("version").asLong()).isEqualTo(1L);
    }

    @Test
    void customerUpdateRequiresIfMatchAndDetectsConflicts() {
        // seed customer c-2 is untouched at v0
        ResponseEntity<String> missing = rest.exchange("/customers/c-2", HttpMethod.PUT,
                new HttpEntity<>("""
                        {"name":"Bob X","email":"bobx@example.com"}""", jsonHeaders()),
                String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(428);

        HttpHeaders stale = jsonHeaders();
        stale.set(HttpHeaders.IF_MATCH, "\"c-2:v42\"");
        ResponseEntity<String> conflict = rest.exchange("/customers/c-2", HttpMethod.PUT,
                new HttpEntity<>("""
                        {"name":"Bob X","email":"bobx@example.com"}""", stale), String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(412);

        HttpHeaders current = jsonHeaders();
        current.set(HttpHeaders.IF_MATCH, "\"c-2:v0\"");
        ResponseEntity<String> ok = rest.exchange("/customers/c-2", HttpMethod.PUT,
                new HttpEntity<>("""
                        {"name":"Bob X","email":"bobx@example.com"}""", current), String.class);
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ok.getHeaders().getETag()).isEqualTo("\"c-2:v1\"");
        JsonNode updated = Json.parse(json, ok.getBody());
        assertThat(updated.path("version").asLong()).isEqualTo(1L);
        assertThat(updated.path("name").asText()).isEqualTo("Bob X");
    }

    @Test
    void repeatedIdempotentSubmissionReplaysWithoutSecondChange() {
        String id = createFreshOrder();
        String key = "key-ship-" + id;

        ResponseEntity<String> first = transition(id, "ship", key, null);
        // CREATED cannot ship directly: drive it to PAID first. Restart flow:
        if (first.getStatusCode().value() == 409) {
            // key was consumed by a failed attempt and cleared; pay then ship.
            assertThat(transition(id, "pay", null, null).getStatusCode().is2xxSuccessful()).isTrue();
            first = transition(id, "ship", key, null);
        }
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        long versionAfterFirst = getOrder(id).path("version").asLong();
        assertThat(versionAfterFirst).isEqualTo(2L);

        ResponseEntity<String> replay = transition(id, "ship", key, null);
        assertThat(replay.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode replayed = Json.parse(json, replay.getBody());
        assertThat(replayed.path("state").asText()).isEqualTo("shipped");
        assertThat(getOrder(id).path("version").asLong()).isEqualTo(versionAfterFirst);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIs422() {
        String id = createFreshOrder();
        assertThat(transition(id, "pay", "shared-key", null).getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<String> reused = transition(id, "ship", "shared-key", null);
        assertThat(reused.getStatusCode().value()).isEqualTo(422);
        assertThat(Json.parse(json, reused.getBody()).path("error").path("code").asText())
                .isEqualTo("invalid-request");
    }

    @Test
    void concurrentIdenticalTransitionsYieldExactlyOneStateChange() throws Exception {
        String id = createFreshOrder();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ResponseEntity<String> response = transition(id, "pay", null, null);
                if (response.getStatusCode().is2xxSuccessful()) {
                    ok.incrementAndGet();
                } else if (response.getStatusCode().value() == 409) {
                    conflict.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
        JsonNode finalOrder = getOrder(id);
        assertThat(finalOrder.path("state").asText()).isEqualTo("paid");
        assertThat(finalOrder.path("version").asLong()).isEqualTo(1L);
    }

    @Test
    void failedTransitionRollsNothingLeavesConsistentState() {
        String id = createFreshOrder();
        ResponseEntity<String> response = transition(id, "deliver", null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode order = getOrder(id);
        assertThat(order.path("state").asText()).isEqualTo("created");
        assertThat(order.path("version").asLong()).isZero();
        // Links still advertise only legal transitions.
        assertThat(order.path("_links").has("pay")).isTrue();
        assertThat(order.path("_links").has("deliver")).isFalse();
    }
}
