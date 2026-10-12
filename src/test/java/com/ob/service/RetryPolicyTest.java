package com.ob.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(1000, 60000, 0.2);

    @Test
    void firstRetryIsBaseDelayPlusJitter() {
        for (int i = 0; i < 100; i++) {
            assertThat(policy.nextDelay(1)).isBetween(1000L, 1200L);
        }
    }

    @Test
    void delayDoublesEachAttempt() {
        for (int i = 0; i < 100; i++) {
            assertThat(policy.nextDelay(2)).isBetween(2000L, 2400L);
            assertThat(policy.nextDelay(4)).isBetween(8000L, 9600L);
        }
    }

    @Test
    void delayNeverGoesAboveMax() {
        assertThat(policy.nextDelay(20)).isEqualTo(60000L);
        assertThat(policy.nextDelay(500)).isEqualTo(60000L);
    }
}
