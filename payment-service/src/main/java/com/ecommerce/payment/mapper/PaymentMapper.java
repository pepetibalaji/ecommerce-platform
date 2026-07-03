package com.ecommerce.payment.mapper;

import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "attempts", ignore = true)
    @Mapping(target = "refunds", ignore = true)
    @Mapping(target = "webhookEvents", ignore = true)
    Payment toEntity(CreatePaymentRequest request);
}