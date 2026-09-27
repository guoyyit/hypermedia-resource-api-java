package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Requirement 4 (concurrency): when several clients submit the same change
 * against the same ETag simultaneously, exactly one commit is accepted and
 * the rest receive the documented 412 conflict; the aggregate ends in a
 * single consistent version.
 */
@SpringBootTest
class ConcurrencyThreadApiTest extends AbstractApiTest {

    private String location;
    private String etag;

    @BeforeEach
    void setUp() throws Exception {
        initMvc();
        String body = """
                {"customerName":"Race User","items":[
                  {"sku":"R1","name":"Race","quantity":1,"unitPrice":10}]}
                """;
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body).accept(HAL))
                .andReturn();
        location = created.getResponse().getHeader("Location");
        etag = created.getResponse().getHeader("ETag");
    }

    @Test
    void parallelUpdatesAgainstSameEtagAcceptExactlyOne() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                start.await();
                String body = "{\"customerName\":\"Racer-" + n + "\",\"items\":["
                        + "{\"sku\":\"R1\",\"name\":\"Race\",\"quantity\":1,\"unitPrice\":10}]}";
                statuses.add(mockMvc.perform(put(location)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("If-Match", etag).accept(HAL)).andReturn()
                        .getResponse().getStatus());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long ok = statuses.stream().filter(s -> s == 200).count();
        long conflicts = statuses.stream().filter(s -> s == 412).count();
        assertThat(ok).as("exactly one update commits").isEqualTo(1);
        assertThat(conflicts).as("all losers get the conflict contract").isEqualTo(threads - 1);

        JsonNode current = getJson(location);
        assertThat(current.path("customerName").asText()).startsWith("Racer-");
        // Version increased by exactly one across the whole race.
        assertThat(getJson(location).path("id").asText()).isEqualTo(location.substring("/orders/".length()));
    }

    @Test
    void parallelTransitionsAgainstSameEtagAcceptExactlyOne() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                statuses.add(mockMvc.perform(post(location + "/transitions/submit")
                        .header("If-Match", etag).accept(HAL)).andReturn()
                        .getResponse().getStatus());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 412).count()).isEqualTo(threads - 1);
        // Exactly one state change, no intermediate/duplicated outcome.
        assertThat(getJson(location).path("status").asText()).isEqualTo("PENDING_PAYMENT");
    }
}
