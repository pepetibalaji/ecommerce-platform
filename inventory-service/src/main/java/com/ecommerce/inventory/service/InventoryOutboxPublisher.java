package com.ecommerce.inventory.service;
import com.ecommerce.inventory.entity.InventoryEventOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor class InventoryOutboxPublisher {
 private final InventoryOutboxService outbox; private final KafkaTemplate<String,Object> kafka; private final ObjectMapper mapper;
 @Scheduled(fixedDelayString="${inventory.outbox.poll-delay:1000}") void publish() { for(int i=0;i<100;i++) { InventoryEventOutbox e=outbox.claim(); if(e==null)return; try { kafka.send(e.getTopic(),e.getMessageKey(),mapper.readTree(e.getPayload())).get(); outbox.published(e); } catch(Exception x) { if(x instanceof InterruptedException) Thread.currentThread().interrupt(); outbox.failed(e,x); } } }
}
