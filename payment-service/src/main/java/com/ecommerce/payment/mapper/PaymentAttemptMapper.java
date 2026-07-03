package com.ecommerce.payment.mapper;

import com.ecommerce.payment.dto.request.CreatePaymentAttemptRequest;
import com.ecommerce.payment.dto.response.PaymentAttemptResponse;
import com.ecommerce.payment.entity.PaymentAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentAttemptMapper {

    @Mapping(source = "payment.id", target = "paymentId")
    PaymentAttemptResponse toResponse(PaymentAttempt paymentAttempt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentAttempt toEntity(CreatePaymentAttemptRequest request);
}