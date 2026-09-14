package com.ecommerce.order.repository;

import java.util.UUID;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("select distinct o from Order o join o.items i where i.sellerId = :sellerId")
    Page<Order> findBySellerId(@Param("sellerId") UUID sellerId, Pageable pageable);

    @Query(value = "select * from orders where id = :id for update", nativeQuery = true)
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select o.* from orders o where o.status = 'PENDING' and o.payment_expires_at <= :now and not exists (select 1 from order_refund_request_outbox c where c.order_id = o.id and c.command_type = 'EXPIRY') order by o.payment_expires_at, o.id limit :batchSize for update of o skip locked", nativeQuery = true)
    List<Order> lockExpiredPending(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
