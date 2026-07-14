package com.ecommerce.payment.entity;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_payments_user_id", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_provider", columnList = "provider"),
                @Index(name = "idx_payments_created_at", columnList = "created_at"),
                @Index(name = "idx_payments_correlation_id", columnList = "correlation_id"),
                @Index(name = "idx_payments_trace_id", columnList = "trace_id")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @NotNull(message = "Order id is required")
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @NotNull(message = "User id is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Amount must have up to 17 integer digits and 2 decimal places")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull(message = "Payment status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PaymentStatus status;

    @NotNull(message = "Payment provider is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private PaymentProvider provider;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 150, message = "Idempotency key must not exceed 150 characters")
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Size(max = 5000, message = "Failure reason must not exceed 5000 characters")
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Size(max = 128, message = "Correlation id must not exceed 128 characters")
    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Size(max = 128, message = "Trace id must not exceed 128 characters")
    @Column(name = "trace_id", length = 128)
    private String traceId;

    @NotNull(message = "Created timestamp is required")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull(message = "Updated timestamp is required")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<PaymentAttempt> attempts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<PaymentRefund> refunds = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<PaymentWebhookEvent> webhookEvents = new ArrayList<>();

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
            status = PaymentStatus.PENDING;
        }

        if (provider == null) {
            provider = PaymentProvider.STRIPE;
        }

        normalizeCurrency();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeCurrency();
    }

    private void normalizeCurrency() {
        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
    }
}