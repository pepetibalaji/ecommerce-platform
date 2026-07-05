package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentResponse {

    private UUID paymentId;

    private UUID orderId;

    private UUID userId;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private PaymentProvider provider;

    private String failureReason;

    private PaymentAttemptResponse latestAttempt;

    private List<PaymentRefundResponse> refunds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}