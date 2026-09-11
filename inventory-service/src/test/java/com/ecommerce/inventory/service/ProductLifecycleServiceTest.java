package com.ecommerce.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import com.ecommerce.inventory.repository.InventoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductLifecycleServiceTest {
    @Mock InventoryRepository inventories;
    @InjectMocks ProductLifecycleService lifecycle;

    @Test
    void rejectsUnsupportedSchemaAndContradictoryLifecycleStateBeforeWriting() {
        assertThatThrownBy(() -> lifecycle.apply(event(2, 1, "product.created", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.apply(event(1, 0, "product.created", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.apply(event(1, 1, "product.archived", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.apply(event(1, 1, "product.reactivated", false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.apply(event(1, 1, "product.unknown", true)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(inventories);
    }

    @Test
    void reportsAppliedAndIgnoredSnapshotsFromAtomicDatabaseUpdate() {
        when(inventories.applyProductSnapshot(any(), any(), any(), anyBoolean(), anyLong(), any()))
                .thenReturn(1, 0);
        ProductLifecycleEvent snapshot = event(1, 8, "product.reconciled", false);
        assertThat(lifecycle.apply(snapshot)).isTrue();
        assertThat(lifecycle.apply(snapshot)).isFalse();
    }

    private ProductLifecycleEvent event(int schema, long version, String type, boolean active) {
        return new ProductLifecycleEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), type, schema, version, active, "Product", BigDecimal.TEN, "INR", null,
                "Description", "Category", "Brand", List.of());
    }
}
