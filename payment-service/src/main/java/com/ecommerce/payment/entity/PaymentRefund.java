package com.ecommerce.payment.entity;

import com.ecommerce.payment.enums.RefundStatus;
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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "payment_refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_refunds_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_payment_refunds_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_refunds_status", columnList = "status"),
                @Index(name = "idx_payment_refunds_created_at", columnList = "created_at")
        }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PaymentRefund {

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
            foreignKey = @ForeignKey(name = "fk_payment_refunds_payment")
    )
    @ToString.Exclude
    private Payment payment;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have up to 17 integer digits and 2 decimal places")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Size(max = 255, message = "Provider refund id must not exceed 255 characters")
    @Column(name = "provider_refund_id", length = 255)
    private String providerRefundId;

    @NotNull(message = "Refund status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private RefundStatus status;

    @Size(max = 5000, message = "Refund reason must not exceed 5000 characters")
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Size(max = 5000, message = "Failure reason must not exceed 5000 characters")
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 150, message = "Idempotency key must not exceed 150 characters")
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

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
            status = RefundStatus.REFUND_REQUESTED;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}