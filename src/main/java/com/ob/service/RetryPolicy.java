package com.ob.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class RetryPolicy {

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double jitter;
    private final Random random = new Random();

    public RetryPolicy(@Value("${outbox.retry.base-delay-ms:1000}") long baseDelayMs,
                       @Value("${outbox.retry.max-delay-ms:300000}") long maxDelayMs,
                       @Value("${outbox.retry.jitter:0.2}") double jitter) {
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitter = jitter;
    }

    // base * 2^(attempt-1)
    public long nextDelay(int attempt) {
        long delay = baseDelayMs;
        for (int i = 1; i < attempt && delay < maxDelayMs; i++) {
            delay = delay * 2;
        }

        // jitter
        long extra = (long) (delay * jitter * random.nextDouble());
        delay = delay + extra;

        return Math.min(delay, maxDelayMs);
    }
}
