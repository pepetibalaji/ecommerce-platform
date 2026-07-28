package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentMetrics paymentMetrics;
    @InjectMocks private PaymentServiceImpl paymentService;

    @Test
    void createPaymentPersistsPendingPaymentAndRecordsMetric() {
        UUID orderId = UUID.randomUUID();
        CreatePaymentRequest request = request(orderId, "payment-create-1");
        Payment entity = Payment.builder().orderId(orderId).build();
        PaymentResponse expected = new PaymentResponse();
        expected.setOrderId(orderId);
        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(paymentRepository.existsByIdempotencyKey("payment-create-1")).thenReturn(false);
        when(paymentMapper.toEntity(request)).thenReturn(entity);
        when(paymentRepository.save(entity)).thenReturn(entity);
        when(paymentMapper.toResponse(entity)).thenReturn(expected);

        assertThat(paymentService.createPayment(request)).isSameAs(expected);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentMetrics).paymentCreated();
    }

    @Test
    void createPaymentRejectsDuplicateOrderBeforeMappingOrSaving() {
        UUID orderId = UUID.randomUUID();
        CreatePaymentRequest request = request(orderId, "duplicate-order");
        when(paymentRepository.existsByOrderId(orderId)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(ResourceAlreadyExistsException.class);

        verify(paymentMapper, never()).toEntity(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentMetrics);
    }

    private CreatePaymentRequest request(UUID orderId, String idempotencyKey) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(orderId);
        request.setUserId(UUID.randomUUID());
        request.setAmount(new BigDecimal("12.50"));
        request.setCurrency("USD");
        request.setProvider(PaymentProvider.SANDBOX);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
