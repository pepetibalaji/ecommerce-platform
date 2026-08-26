package com.ecommerce.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payment_confirmed_at")
    private LocalDateTime paymentConfirmedAt;

    @Column(name = "payment_failed_at")
    private LocalDateTime paymentFailedAt;

    @Column(name = "payment_failure_reason", columnDefinition = "TEXT")
    private String paymentFailureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "shipping_address_id")
    private UUID shippingAddressId;

    @Size(max = 150)
    @Column(name = "shipping_recipient_name", length = 150)
    private String shippingRecipientName;

    @Size(max = 30)
    @Column(name = "shipping_phone", length = 30)
    private String shippingPhone;

    @Size(max = 255)
    @Column(name = "shipping_line1", length = 255)
    private String shippingLine1;

    @Size(max = 255)
    @Column(name = "shipping_line2", length = 255)
    private String shippingLine2;

    @Size(max = 100)
    @Column(name = "shipping_city", length = 100)
    private String shippingCity;

    @Size(max = 100)
    @Column(name = "shipping_state", length = 100)
    private String shippingState;

    @Size(max = 30)
    @Column(name = "shipping_postal_code", length = 30)
    private String shippingPostalCode;

    @Size(max = 2)
    @Column(name = "shipping_country", length = 2)
    private String shippingCountry;

    @NotNull(message = "Updated timestamp is required")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        LocalDateTime now = LocalDateTime.now();

        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = OrderStatus.PENDING;
        }
    }
    
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
