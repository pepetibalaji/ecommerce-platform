package com.ecommerce.common.events.dlq;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.time.Instant;

public class DeadLetterEvent extends AbstractDomainEvent {

  private String originalTopic;
  private String originalKey;
  private String originalEventType;
  private Integer originalPartition;
  private Long originalOffset;
  private String consumerGroup;
  private Integer retryCount;
  private String payload;
  private String exceptionClass;
  private String errorMessage;
  private Instant failedAt;

  public DeadLetterEvent() {
    super(EventTypes.DEAD_LETTER, EventSources.KAFKA_ERROR_HANDLER, null, null);
    this.failedAt = Instant.now();
  }

  public DeadLetterEvent(
      String originalTopic,
      String originalKey,
      String originalEventType,
      String payload,
      String exceptionClass,
      String errorMessage,
      String correlationId,
      String traceId) {
    this(
        originalTopic,
        originalKey,
        originalEventType,
        null,
        null,
        null,
        null,
        payload,
        exceptionClass,
        errorMessage,
        correlationId,
        traceId);
  }

  public DeadLetterEvent(
      String originalTopic,
      String originalKey,
      String originalEventType,
      Integer originalPartition,
      Long originalOffset,
      String consumerGroup,
      Integer retryCount,
      String payload,
      String exceptionClass,
      String errorMessage,
      String correlationId,
      String traceId) {
    super(EventTypes.DEAD_LETTER, EventSources.KAFKA_ERROR_HANDLER, correlationId, traceId);
    this.originalTopic = originalTopic;
    this.originalKey = originalKey;
    this.originalEventType = originalEventType;
    this.originalPartition = originalPartition;
    this.originalOffset = originalOffset;
    this.consumerGroup = consumerGroup;
    this.retryCount = retryCount;
    this.payload = payload;
    this.exceptionClass = exceptionClass;
    this.errorMessage = errorMessage;
    this.failedAt = Instant.now();
  }

  public String getOriginalTopic() {
    return originalTopic;
  }

  public void setOriginalTopic(String originalTopic) {
    this.originalTopic = originalTopic;
  }

  public String getOriginalKey() {
    return originalKey;
  }

  public void setOriginalKey(String originalKey) {
    this.originalKey = originalKey;
  }

  public String getOriginalEventType() {
    return originalEventType;
  }

  public void setOriginalEventType(String originalEventType) {
    this.originalEventType = originalEventType;
  }

  public Integer getOriginalPartition() {
    return originalPartition;
  }

  public void setOriginalPartition(Integer originalPartition) {
    this.originalPartition = originalPartition;
  }

  public Long getOriginalOffset() {
    return originalOffset;
  }

  public void setOriginalOffset(Long originalOffset) {
    this.originalOffset = originalOffset;
  }

  public String getConsumerGroup() {
    return consumerGroup;
  }

  public void setConsumerGroup(String consumerGroup) {
    this.consumerGroup = consumerGroup;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getExceptionClass() {
    return exceptionClass;
  }

  public void setExceptionClass(String exceptionClass) {
    this.exceptionClass = exceptionClass;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public void setFailedAt(Instant failedAt) {
    this.failedAt = failedAt;
  }
}
