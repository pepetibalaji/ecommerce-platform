package com.ecommerce.payment.service;

import com.ecommerce.payment.entity.PaymentCancellationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A lookup/preparation failure must not roll back the cancellation inbox's retry schedule. */
@Service
@RequiredArgsConstructor
public class PaymentCancellationPreparationService {
    private final PaymentService payments;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepare(PaymentCancellationRequest command) {
        payments.preparePaymentFromOrder(command.getOrderId(), command.getUserId(), command.getAmount(),
                command.getCurrency(), command.getCorrelationId(), command.getTraceId());
    }
}