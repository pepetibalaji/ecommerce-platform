package com.ecommerce.order.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReleaseOutboxRepositoryTest {

    @Test
    void lockNextPending_shouldSelectOnlyDueCommandsAndSkipRowsLockedByOtherWorkers() throws Exception {
        Method method = InventoryReleaseOutboxRepository.class
                .getMethod("lockNextPending", int.class, java.time.LocalDateTime.class);
        String query = method.getAnnotation(Query.class).value().toLowerCase();

        assertThat(query).contains("status = 'pending'");
        assertThat(query).contains("next_attempt_at <= :now");
        assertThat(query).contains("for update skip locked");
    }
}
