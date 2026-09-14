package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentWebhookService;
import com.ecommerce.payment.webhook.VerifiedWebhookInbox;
import com.ecommerce.payment.webhook.VerifiedWebhookProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentMetrics paymentMetrics;
    private final VerifiedWebhookInbox inbox;
    private final VerifiedWebhookProcessor processor;

    /** Compatibility endpoint: browser requests can query, but can never confirm or fail payment. */
    @Override
    @Transactional(readOnly=true)
    public PaymentResponse refreshPayment(UUID orderId,UUID userId) {
        var payment=paymentRepository.findByOrderId(orderId).orElseThrow(()->new PaymentApiException(PaymentErrorCode.PAYMENT_PREPARING));
        if(!payment.getUserId().equals(userId)) throw new PaymentApiException(PaymentErrorCode.PAYMENT_NOT_OWNED);
        return paymentMapper.toResponse(payment);
    }

    @Override
    public WebhookAckResponse processWebhook(PaymentProvider provider,String payload,String signature) {
        var gateway=paymentGatewayFactory.getGateway(provider);
        com.ecommerce.payment.provider.model.ProviderWebhookEvent verified;
        try {
            verified=paymentMetrics.recordProviderLatency(provider,()->gateway.parseWebhookEvent(payload,signature));
            if(verified==null || verified.getProvider()!=provider || verified.getProviderEventId()==null
                    || verified.getProviderEventId().isBlank() || verified.getEventType()==null)
                throw new IllegalArgumentException("Invalid verified event");
        } catch(RuntimeException ex) {
            paymentMetrics.webhookInvalidSignature(provider);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid webhook signature or event");
        }
        paymentMetrics.webhookReceived(provider);
        // This commits BEFORE processing. The verified envelope survives handler/DB/provider-ordering failures.
        var receipt=inbox.accept(verified,payload);
        if(receipt.duplicate()) paymentMetrics.webhookDuplicate(provider);
        WebhookProcessingStatus status;
        try { status=processor.process(receipt.id()); }
        catch(RuntimeException ex) { inbox.failed(receipt.id(),ex); status=WebhookProcessingStatus.RECEIVED; }
        return WebhookAckResponse.builder().received(true).duplicate(receipt.duplicate())
                .processingStatus(status).message("Webhook received").build();
    }
}
