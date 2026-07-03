package com.ecommerce.payment.mapper;

import com.ecommerce.payment.dto.request.CreatePaymentRefundRequest;
import com.ecommerce.payment.dto.response.PaymentRefundResponse;
import com.ecommerce.payment.entity.PaymentRefund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentRefundMapper {

    @Mapping(source = "payment.id", target = "paymentId")
    PaymentRefundResponse toResponse(PaymentRefund paymentRefund);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "status", constant = "REFUND_REQUESTED")
    @Mapping(target = "providerRefundId", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentRefund toEntity(CreatePaymentRefundRequest request);
}