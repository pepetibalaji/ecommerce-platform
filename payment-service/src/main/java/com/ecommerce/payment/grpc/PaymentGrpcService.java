package com.ecommerce.payment.grpc;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.response.AdminRefundResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import com.ecommerce.proto.payment.GetPaymentStatusRequest;
import com.ecommerce.proto.payment.GetPaymentStatusResponse;
import com.ecommerce.proto.payment.PaymentServiceGrpc;
import com.ecommerce.proto.payment.ProcessPaymentRequest;
import com.ecommerce.proto.payment.ProcessPaymentResponse;
import com.ecommerce.proto.payment.RefundPaymentRequest;
import com.ecommerce.proto.payment.RefundPaymentResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final String PROCESS_PAYMENT_IDEMPOTENCY_PREFIX = "grpc-process-payment:";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentRefundService paymentRefundService;

    @Override
    @Transactional
    public void processPayment(
            ProcessPaymentRequest request,
            StreamObserver<ProcessPaymentResponse> responseObserver
    ) {
        try {
            ProcessPaymentResponse response = processPaymentInternal(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception exception) {
            responseObserver.onError(toGrpcError(exception));
        }
    }

    @Override
    @Transactional
    public void refundPayment(
            RefundPaymentRequest request,
            StreamObserver<RefundPaymentResponse> responseObserver
    ) {
        try {
            UUID paymentId = parseUuid(
                    request.getPaymentId(),
                    "payment_id is required"
            );

            UUID orderId = parseUuid(
                    request.getOrderId(),
                    "order_id is required"
            );

            BigDecimal amount = parseAmount(
                    request.getAmount(),
                    "amount is required"
            );

            String currency = normalizeCurrency(request.getCurrency());
            String idempotencyKey = requireText(
                    request.getIdempotencyKey(),
                    "idempotency_key is required"
            );

            AdminRefundResponse result = paymentRefundService.refundPayment(
                    paymentId,
                    orderId,
                    amount,
                    currency,
                    request.getReason(),
                    idempotencyKey
            );

            RefundPaymentResponse response = RefundPaymentResponse.newBuilder()
                    .setPaymentId(s(result.paymentId()))
                    .setRefundId(s(result.refundId()))
                    .setStatus(s(result.status()))
                    .setProviderRefundId(s(result.providerRefundId()))
                    .setFailureReason(s(result.failureReason()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception exception) {
            responseObserver.onError(toGrpcError(exception));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getPaymentStatus(
            GetPaymentStatusRequest request,
            StreamObserver<GetPaymentStatusResponse> responseObserver
    ) {
        try {
            UUID orderId = parseUuid(
                    request.getOrderId(),
                    "order_id is required"
            );

            Payment payment = paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Payment not found for order: " + orderId
                    ));

            GetPaymentStatusResponse response = GetPaymentStatusResponse.newBuilder()
                    .setOrderId(s(payment.getOrderId()))
                    .setPaymentId(s(payment.getId()))
                    .setStatus(s(payment.getStatus()))
                    .setProvider(s(payment.getProvider()))
                    .setFailureReason(s(payment.getFailureReason()))
                    .setUpdatedAt(s(payment.getUpdatedAt()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception exception) {
            responseObserver.onError(toGrpcError(exception));
        }
    }

    private ProcessPaymentResponse processPaymentInternal(ProcessPaymentRequest request) {
        UUID orderId = parseUuid(
                request.getOrderId(),
                "order_id is required"
        );

        UUID userId = parseUuid(
                request.getUserId(),
                "user_id is required"
        );

        BigDecimal amount = parseAmount(
                request.getAmount(),
                "amount is required"
        );

        String currency = normalizeCurrency(request.getCurrency());

        String idempotencyKey = normalizeProcessPaymentIdempotencyKey(
                orderId,
                request.getIdempotencyKey()
        );

        Payment payment = findOrCreatePayment(
                orderId,
                userId,
                amount,
                currency,
                idempotencyKey
        );

        PaymentAttempt activeAttempt = findReusableActiveAttempt(payment);

        if (activeAttempt != null) {
            return toProcessPaymentResponse(
                    payment,
                    activeAttempt,
                    activeAttempt.getFailureReason()
            );
        }

        PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getProvider());

        CheckoutSessionResult checkoutSession = gateway.createCheckoutSession(
                CreateCheckoutSessionCommand.builder()
                        .paymentId(payment.getId())
                        .orderId(payment.getOrderId())
                        .userId(payment.getUserId())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .idempotencyKey(idempotencyKey)
                        .build()
        );

        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(payment)
                .provider(checkoutSession.getProvider())
                .providerSessionId(checkoutSession.getProviderSessionId())
                .providerPaymentIntentId(checkoutSession.getProviderPaymentIntentId())
                .checkoutUrl(checkoutSession.getCheckoutUrl())
                .expiresAt(checkoutSession.getExpiresAt())
                .status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION)
                .idempotencyKey(idempotencyKey)
                .failureReason(null)
                .build();

        payment.setProvider(checkoutSession.getProvider());
        payment.setStatus(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        payment.setFailureReason(null);

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);
        Payment savedPayment = paymentRepository.save(payment);

        log.info(
                "gRPC ProcessPayment created checkout session. paymentId={}, orderId={}, provider={}, providerSessionId={}",
                savedPayment.getId(),
                savedPayment.getOrderId(),
                savedAttempt.getProvider(),
                savedAttempt.getProviderSessionId()
        );

        return toProcessPaymentResponse(
                savedPayment,
                savedAttempt,
                null
        );
    }

    private Payment findOrCreatePayment(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        return paymentRepository.findByOrderId(orderId)
                .map(existing -> {
                    validateExistingPayment(
                            existing,
                            userId,
                            amount,
                            currency
                    );

                    return existing;
                })
                .orElseGet(() -> createPayment(
                        orderId,
                        userId,
                        amount,
                        currency,
                        idempotencyKey
                ));
    }

    private Payment createPayment(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .provider(PaymentProvider.STRIPE)
                .idempotencyKey(idempotencyKey)
                .failureReason(null)
                .build();

        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            return paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> exception);
        }
    }

    private void validateExistingPayment(
            Payment payment,
            UUID userId,
            BigDecimal amount,
            String currency
    ) {
        if (!payment.getUserId().equals(userId)) {
            throw new BadRequestException(
                    "Payment user does not match request user for order: " + payment.getOrderId()
            );
        }

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new BadRequestException(
                    "Payment amount does not match existing payment for order: " + payment.getOrderId()
            );
        }

        if (!payment.getCurrency().equals(currency)) {
            throw new BadRequestException(
                    "Payment currency does not match existing payment for order: " + payment.getOrderId()
            );
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.REFUND_PROCESSING
                || payment.getStatus() == PaymentStatus.REFUND_REQUESTED) {
            throw new ResourceAlreadyExistsException(
                    "Payment is already completed or refunding for order: " + payment.getOrderId()
            );
        }
    }

    private PaymentAttempt findReusableActiveAttempt(Payment payment) {
        return paymentAttemptRepository
                .findTopByPayment_IdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        payment.getId(),
                        List.of(
                                PaymentAttemptStatus.CREATED,
                                PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION,
                                PaymentAttemptStatus.PROCESSING
                        ),
                        LocalDateTime.now()
                )
                .filter(attempt -> hasText(attempt.getCheckoutUrl()))
                .filter(attempt -> hasText(attempt.getProviderSessionId()))
                .orElse(null);
    }

    private ProcessPaymentResponse toProcessPaymentResponse(
            Payment payment,
            PaymentAttempt attempt,
            String failureReason
    ) {
        return ProcessPaymentResponse.newBuilder()
                .setPaymentId(s(payment.getId()))
                .setOrderId(s(payment.getOrderId()))
                .setStatus(s(payment.getStatus()))
                .setProvider(s(payment.getProvider()))
                .setCheckoutUrl(s(attempt.getCheckoutUrl()))
                .setProviderSessionId(s(attempt.getProviderSessionId()))
                .setExpiresAt(s(attempt.getExpiresAt()))
                .setFailureReason(s(failureReason))
                .build();
    }

    private UUID parseUuid(
            String value,
            String message
    ) {
        String text = requireText(
                value,
                message
        );

        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(message + ": must be a valid UUID");
        }
    }

    private BigDecimal parseAmount(
            String value,
            String message
    ) {
        String text = requireText(
                value,
                message
        );

        try {
            BigDecimal amount = new BigDecimal(text);

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("amount must be greater than zero");
            }

            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("amount must have at most 2 decimal places");
        } catch (NumberFormatException exception) {
            throw new BadRequestException("amount must be a valid decimal number");
        }
    }

    private String normalizeCurrency(String currency) {
        String normalized = requireText(
                currency,
                "currency is required"
        ).trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new BadRequestException(
                    "currency must be a 3-letter ISO code, for example INR or USD"
            );
        }

        return normalized;
    }

    private String normalizeProcessPaymentIdempotencyKey(
            UUID orderId,
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return PROCESS_PAYMENT_IDEMPOTENCY_PREFIX + orderId;
        }

        return idempotencyKey.trim();
    }

    private String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }

    private Throwable toGrpcError(Exception exception) {
        if (exception instanceof ResourceNotFoundException) {
            return Status.NOT_FOUND
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        if (exception instanceof ResourceAlreadyExistsException) {
            return Status.ALREADY_EXISTS
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        if (exception instanceof BadRequestException
                || exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        log.error("Unexpected Payment gRPC error", exception);

        return Status.INTERNAL
                .withDescription("Payment service internal error")
                .asRuntimeException();
    }

    private String s(UUID value) {
        return value == null ? "" : value.toString();
    }

    private String s(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String s(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String s(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}