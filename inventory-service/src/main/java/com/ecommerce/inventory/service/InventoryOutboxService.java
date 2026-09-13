package com.ecommerce.inventory.service;

import com.ecommerce.inventory.entity.InventoryEventOutbox;
import com.ecommerce.inventory.repository.InventoryEventOutboxRepository;
import com.ecommerce.inventory.dto.InventoryOperationsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class InventoryOutboxService {
 private final InventoryEventOutboxRepository repository; private final ObjectMapper objectMapper; private final MeterRegistry meters;
 @Transactional
 public void enqueue(String topic, String key, Object payload) {
  try { repository.save(new InventoryEventOutbox(topic,key,objectMapper.writeValueAsString(payload))); meters.counter("inventory_outbox_enqueued_total").increment(); }
  catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize inventory event", e); }
 }
 @Transactional public InventoryEventOutbox claim() {
  return repository.findDueForUpdate(Instant.now(), org.springframework.data.domain.PageRequest.of(0,1)).stream().findFirst().map(event -> { event.setStatus(InventoryEventOutbox.Status.PROCESSING); event.setAttempts(event.getAttempts()+1); event.setLeaseUntil(Instant.now().plusSeconds(60)); return event; }).orElse(null);
 }
 @Transactional public void published(InventoryEventOutbox e) { e.setStatus(InventoryEventOutbox.Status.PUBLISHED); e.setPublishedAt(Instant.now()); e.setLeaseUntil(null); e.setLastError(null); repository.save(e); meters.counter("inventory_outbox_delivered_total").increment(); }
 @Transactional public void failed(InventoryEventOutbox e, Exception failure) { boolean dead=e.getAttempts()>=10; e.setStatus(dead?InventoryEventOutbox.Status.DEAD:InventoryEventOutbox.Status.PENDING); e.setLeaseUntil(null); e.setLastError(failure.getClass().getSimpleName()); e.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 5L << Math.min(10,e.getAttempts()-1)))); repository.save(e); meters.counter(dead?"inventory_outbox_dead_total":"inventory_outbox_failures_total").increment(); }
 @Transactional(readOnly=true) public InventoryOperationsResponse operations() { return InventoryOperationsResponse.builder()
         .pendingOutboxEvents(repository.countByStatus(InventoryEventOutbox.Status.PENDING) + repository.countByStatus(InventoryEventOutbox.Status.PROCESSING))
         .deadOutboxEvents(repository.countByStatus(InventoryEventOutbox.Status.DEAD)).generatedAt(Instant.now()).build(); }
}
