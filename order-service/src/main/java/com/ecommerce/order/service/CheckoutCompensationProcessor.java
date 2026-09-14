package com.ecommerce.order.service;
import com.ecommerce.order.repository.CheckoutCompensationOutboxRepository; import com.ecommerce.order.grpc.InventoryGrpcClient;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.beans.factory.annotation.Value; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Component @RequiredArgsConstructor @Slf4j
public class CheckoutCompensationProcessor {
 private final CheckoutCompensationOutboxRepository repository; private final InventoryGrpcClient inventory; private final Clock clock;
 @Value("${order.checkout-compensation.batch-size:25}") int batchSize; @Value("${order.checkout-compensation.max-attempts:8}") int maxAttempts;
 @Scheduled(fixedDelayString="${order.checkout-compensation.fixed-delay-ms:5000}") @Transactional
 public void process() { Instant now=Instant.now(clock); for(var command:repository.lockPending(now,Math.max(1,batchSize))) try {
   inventory.releaseStock(command.getProductId(),command.getQuantity(),command.getReservationId()); command.completed();
 } catch(RuntimeException ex) { command.failed(ex,now.plusSeconds(30),Math.max(1,maxAttempts)); log.error("Checkout compensation failed. reservationId={}",command.getReservationId(),ex); } }
}
