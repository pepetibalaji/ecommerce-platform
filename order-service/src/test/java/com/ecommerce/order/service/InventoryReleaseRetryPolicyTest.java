package com.ecommerce.order.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReleaseRetryPolicyTest {

    @Test
    void delayForAttempt_shouldExponentiallyIncreaseAndCapDelay() {
        InventoryReleaseRetryPolicy policy = new InventoryReleaseRetryPolicy(100, 1_000, 0);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.delayForAttempt(5)).isEqualTo(Duration.ofMillis(1_000));
        assertThat(policy.delayForAttempt(20)).isEqualTo(Duration.ofMillis(1_000));
    }

    @Test
    void delayForAttempt_shouldApplyBoundedJitter() {
        InventoryReleaseRetryPolicy policy = new InventoryReleaseRetryPolicy(1_000, 60_000, 0.20);

        assertThat(policy.delayForAttempt(1).toMillis()).isBetween(800L, 1_200L);
    }
}
