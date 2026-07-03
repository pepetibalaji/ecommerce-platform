package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.RefundStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundResponse {

    @NotNull(message = "Refund id is required")
    private UUID id;

    @NotNull(message = "Payment id is required")
    private UUID paymentId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have up to 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    private String currency;

    @Size(max = 255, message = "Provider refund id must not exceed 255 characters")
    private String providerRefundId;

    @NotNull(message = "Refund status is required")
    private RefundStatus status;

    @Size(max = 5000, message = "Refund reason must not exceed 5000 characters")
    private String reason;

    @Size(max = 5000, message = "Failure reason must not exceed 5000 characters")
    private String failureReason;

    @NotNull(message = "Created timestamp is required")
    private LocalDateTime createdAt;

    @NotNull(message = "Updated timestamp is required")
    private LocalDateTime updatedAt;
}