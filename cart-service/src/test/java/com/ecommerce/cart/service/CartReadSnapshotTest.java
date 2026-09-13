package com.ecommerce.cart.service;

import java.time.Duration;
import java.time.Instant;

import com.ecommerce.cart.config.CartProperties;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.exception.CartBusyException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.redis.key.RedisKeys;
import com.ecommerce.common.redis.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartReadSnapshotTest {

    private static final String OWNER_ID = "owner-123";
    private static final String PRODUCT_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private CartRedisRepository repository;

    @Mock
    private DistributedLockService locks;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(repository, locks, new CartProperties());
        // An unstubbed tryLock returns false: reads must work while writes cannot acquire it.
    }

    @ParameterizedTest(name = "existing snapshot, guest={0}")
    @ValueSource(booleans = { false, true })
    void getCart_shouldReturnExistingSnapshotWithoutLockingOrWriting(boolean guest) {
        Cart stored = new Cart(OWNER_ID);
        stored.getItems().add(new CartItem("item-1", PRODUCT_ID, 2));
        stored.setVersion(7);
        stored.setUpdatedAt(Instant.parse("2026-09-01T12:00:00Z"));
        if (guest) {
            when(repository.findByGuestId(OWNER_ID)).thenReturn(stored);
        } else {
            when(repository.findByUserId(OWNER_ID)).thenReturn(stored);
        }

        CartResponse response = readCart(guest);

        assertThat(response.getOwnerType()).isEqualTo(guest ? "GUEST" : "CUSTOMER");
        assertThat(response.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getItemId()).isEqualTo("item-1");
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getQuantity()).isEqualTo(2);
        });
        assertThat(response.getVersion()).isEqualTo(7);
        assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-01T12:00:00Z"));
        assertThat(stored.getVersion()).isEqualTo(7);
        assertThat(stored.getUpdatedAt()).isEqualTo(response.getUpdatedAt());
        verifyReadOnly(guest);
    }

    @ParameterizedTest(name = "missing snapshot, guest={0}")
    @ValueSource(booleans = { false, true })
    void getCart_shouldReturnEmptySnapshotWithoutLockingOrCreatingStoredCart(boolean guest) {
        CartResponse response = readCart(guest);

        assertThat(response.getOwnerType()).isEqualTo(guest ? "GUEST" : "CUSTOMER");
        assertThat(response.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getVersion()).isZero();
        assertThat(response.getUpdatedAt()).isNotNull();
        verifyReadOnly(guest);
    }

    @ParameterizedTest(name = "contended mutation, guest={0}")
    @ValueSource(booleans = { false, true })
    void addItem_shouldRejectContendedMutationWithoutAccessingRepository(boolean guest) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(2);

        assertThrows(CartBusyException.class, () -> {
            if (guest) {
                service.addGuestItem(OWNER_ID, request);
            } else {
                service.addItem(OWNER_ID, request);
            }
        });

        verify(locks).tryLock(eq(lockKey(guest)), anyString(), eq(Duration.ofSeconds(15)));
        verifyNoMoreInteractions(locks);
        verifyNoInteractions(repository);
    }

    @Test
    void createGuestCart_shouldStillRequireMutationLock() {
        assertThrows(CartBusyException.class, () -> service.createGuestCart(OWNER_ID));

        verify(locks).tryLock(eq(lockKey(true)), anyString(), eq(Duration.ofSeconds(15)));
        verifyNoMoreInteractions(locks);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest(name = "failed mutation releases lock, guest={0}")
    @ValueSource(booleans = { false, true })
    void clearCart_shouldReleaseAcquiredLockWhenRepositoryThrows(boolean guest) {
        String key = lockKey(guest);
        RuntimeException failure = new IllegalStateException("Redis unavailable");
        when(locks.tryLock(eq(key), anyString(), eq(Duration.ofSeconds(15)))).thenReturn(true);
        if (guest) {
            doThrow(failure).when(repository).deleteByGuestId(OWNER_ID);
        } else {
            doThrow(failure).when(repository).deleteByUserId(OWNER_ID);
        }

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            if (guest) {
                service.clearGuestCart(OWNER_ID);
            } else {
                service.clearCart(OWNER_ID);
            }
        });

        assertThat(thrown).isSameAs(failure);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        InOrder ordered = inOrder(locks, repository);
        ordered.verify(locks).tryLock(eq(key), token.capture(), eq(Duration.ofSeconds(15)));
        if (guest) {
            ordered.verify(repository).deleteByGuestId(OWNER_ID);
        } else {
            ordered.verify(repository).deleteByUserId(OWNER_ID);
        }
        ordered.verify(locks).unlock(key, token.getValue());
        ordered.verifyNoMoreInteractions();
    }

    private CartResponse readCart(boolean guest) {
        return guest ? service.getGuestCart(OWNER_ID) : service.getCart(OWNER_ID);
    }

    private void verifyReadOnly(boolean guest) {
        if (guest) {
            verify(repository).findByGuestId(OWNER_ID);
        } else {
            verify(repository).findByUserId(OWNER_ID);
        }
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(locks);
    }

    private String lockKey(boolean guest) {
        return RedisKeys.cartLock(guest ? "guest" : "customer", OWNER_ID);
    }
}
