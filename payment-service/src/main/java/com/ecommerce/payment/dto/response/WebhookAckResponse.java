package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.WebhookProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookAckResponse {

    private boolean received;

    private boolean duplicate;

    private WebhookProcessingStatus processingStatus;

    private String message;
}