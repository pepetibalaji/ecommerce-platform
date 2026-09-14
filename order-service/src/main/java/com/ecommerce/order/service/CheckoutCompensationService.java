package com.ecommerce.order.service;
import com.ecommerce.order.entity.*; import com.ecommerce.order.repository.CheckoutCompensationOutboxRepository;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.*;
import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor
public class CheckoutCompensationService {
 private final CheckoutCompensationOutboxRepository repository; private final Clock clock;
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void enqueue(List<OrderItem> items) {
   Instant now=Instant.now(clock);
   for (OrderItem item:items) if (!repository.existsByReservationId(item.getInventoryReservationId()))
     repository.save(new CheckoutCompensationOutbox(item.getInventoryReservationId(),item.getProductId(),item.getQuantity(),now));
 }
}
