package com.ecommerce.payment.mapper;

import com.ecommerce.payment.dto.request.CreatePaymentWebhookEventRequest;
import com.ecommerce.payment.dto.response.PaymentWebhookEventResponse;
import com.ecommerce.payment.entity.PaymentWebhookEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentWebhookEventMapper {

    @Mapping(source = "payment.id", target = "paymentId")
    PaymentWebhookEventResponse toResponse(PaymentWebhookEvent paymentWebhookEvent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    @Mapping(target = "processedAt", ignore = true)
    PaymentWebhookEvent toEntity(CreatePaymentWebhookEventRequest request);
}