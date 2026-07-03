package com.ecommerce.payment.entity;

import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
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
        name = "payment_attempts",
        indexes = {
                @Index(name = "idx_payment_attempts_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_attempts_status", columnList = "status"),
                @Index(name = "idx_payment_attempts_provider", columnList = "provider"),
                @Index(name = "idx_payment_attempts_created_at", columnList = "created_at")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @NotNull(message = "Payment is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_attempts_payment")
    )
    @ToString.Exclude
    private Payment payment;

    @NotNull(message = "Payment provider is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private PaymentProvider provider;

    @Size(max = 255, message = "Provider session id must not exceed 255 characters")
    @Column(name = "provider_session_id", length = 255)
    private String providerSessionId;

    @Size(max = 255, message = "Provider payment intent id must not exceed 255 characters")
    @Column(name = "provider_payment_intent_id", length = 255)
    private String providerPaymentIntentId;

    @Size(max = 255, message = "Provider charge id must not exceed 255 characters")
    @Column(name = "provider_charge_id", length = 255)
    private String providerChargeId;

    @Size(max = 5000, message = "Checkout URL must not exceed 5000 characters")
    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @NotNull(message = "Payment attempt status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PaymentAttemptStatus status;

    @Size(max = 5000, message = "Failure reason must not exceed 5000 characters")
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @NotNull(message = "Created timestamp is required")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull(message = "Updated timestamp is required")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = PaymentAttemptStatus.CREATED;
        }

        if (provider == null) {
            provider = PaymentProvider.SANDBOX;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}