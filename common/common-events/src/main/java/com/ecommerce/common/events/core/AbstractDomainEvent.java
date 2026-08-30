package com.ecommerce.common.events.core;

import java.time.Instant;
import java.util.UUID;

public abstract class AbstractDomainEvent {

  private UUID eventId;
  private String eventType;
  private String source;
  private Instant occurredAt;
  private String correlationId;
  private String traceId;
  private String schemaVersion;

  protected AbstractDomainEvent() {
    this.eventId = UUID.randomUUID();
    this.occurredAt = Instant.now();
    this.schemaVersion = "1.0";
  }

  protected AbstractDomainEvent(
      String eventType, String source, String correlationId, String traceId) {
    this.eventId = UUID.randomUUID();
    this.eventType = eventType;
    this.source = source;
    this.occurredAt = Instant.now();
    this.correlationId = correlationId;
    this.traceId = traceId;
    this.schemaVersion = "1.0";
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
}
