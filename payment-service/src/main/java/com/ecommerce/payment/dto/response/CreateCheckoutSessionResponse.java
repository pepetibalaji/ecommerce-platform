package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCheckoutSessionResponse {

    private UUID paymentId;

    private UUID orderId;

    private PaymentStatus status;

    private PaymentProvider provider;

    private String checkoutUrl;

    private LocalDateTime expiresAt;
}