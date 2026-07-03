package com.ecommerce.payment.entity;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_webhook_events_provider_event",
                        columnNames = {"provider", "provider_event_id"}
                )
        },
        indexes = {
                @Index(name = "idx_payment_webhook_events_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_webhook_events_processing_status", columnList = "processing_status"),
                @Index(name = "idx_payment_webhook_events_received_at", columnList = "received_at")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @NotNull(message = "Payment provider is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private PaymentProvider provider;

    @NotBlank(message = "Provider event id is required")
    @Size(max = 255, message = "Provider event id must not exceed 255 characters")
    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_id",
            foreignKey = @ForeignKey(name = "fk_payment_webhook_events_payment")
    )
    @ToString.Exclude
    private Payment payment;

    @NotBlank(message = "Event type is required")
    @Size(max = 150, message = "Event type must not exceed 150 characters")
    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @NotNull(message = "Webhook processing status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 40)
    private WebhookProcessingStatus processingStatus;

    @Size(max = 128, message = "Payload hash must not exceed 128 characters")
    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @NotNull(message = "Received timestamp is required")
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }

        if (processingStatus == null) {
            processingStatus = WebhookProcessingStatus.RECEIVED;
        }
    }

    @PreUpdate
    void preUpdate() {
        if (processingStatus == WebhookProcessingStatus.PROCESSED && processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}