package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.error.UnprocessableEntityException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Idempotency support for state-changing requests (POST /orders and
 * POST /orders/{id}/transitions).
 *
 * <p>Contract:
 * <ul>
 *   <li>a request carrying an {@code Idempotency-Key} is processed at most
 *       once: the first outcome (a resource id) is stored and replayed;</li>
 *   <li>a replayed key with a different request fingerprint is rejected with
 *       422 and the documented {@code idempotency-key-reuse} error code;</li>
 *   <li>concurrent requests sharing a key are serialised: the second caller
 *       blocks until the first outcome is available and then replays it.</li>
 * </ul>
 */
@Component
public class IdempotencyService {

    private record StoredOutcome(String requestFingerprint, String resourceId, int status) {
        Replay toReplay() {
            return new Replay(resourceId, status);
        }
    }

    private final ConcurrentMap<String, Object> slots = new ConcurrentHashMap<>();

    private static final class Pending {
    }

    private static final Pending PENDING = new Pending();

    /**
     * Begin processing a keyed request.
     *
     * @return the first outcome to replay, or empty when this caller owns the
     *         first execution and must call {@link #complete} afterwards.
     */
    public Optional<Replay> begin(String key, String requestFingerprint) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Object previous = slots.putIfAbsent(key, PENDING);
        if (previous == null) {
            return Optional.empty();
        }
        synchronized (slots) {
            while (true) {
                Object current = slots.get(key);
                if (current instanceof StoredOutcome outcome) {
                    if (!Objects.equals(outcome.requestFingerprint(), requestFingerprint)) {
                        throw new UnprocessableEntityException(
                                "Idempotency-Key was already used with a different request payload.");
                    }
                    return Optional.of(outcome.toReplay());
                }
                if (current == null) {
                    // Slot was removed by a failed first attempt; retry once.
                    if (slots.putIfAbsent(key, PENDING) == null) {
                        return Optional.empty();
                    }
                    continue;
                }
                try {
                    slots.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for idempotent replay", e);
                }
            }
        }
    }

    /** Record the successful first outcome and release waiting replays. */
    public void complete(String key, String requestFingerprint, String resourceId, int status) {
        if (key == null || key.isBlank()) {
            return;
        }
        synchronized (slots) {
            slots.put(key, new StoredOutcome(requestFingerprint, resourceId, status));
            slots.notifyAll();
        }
    }

    /** Remove the pending slot when the first attempt failed (no state change). */
    public void fail(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        synchronized (slots) {
            slots.remove(key);
            slots.notifyAll();
        }
    }

    /** Outcome record replayed to duplicate submissions. */
    public record Replay(String resourceId, int status) {
    }
}
