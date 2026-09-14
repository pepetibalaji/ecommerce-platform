package com.ecommerce.payment.grpc;

import com.ecommerce.proto.payment.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * The legacy gRPC contract has no authenticated actor and trusted order context.
 * Keep wire-compatible failures during migration; use authenticated HTTP checkout/query
 * or the durable Order cancellation/refund commands instead.
 */
@Deprecated
@GrpcService
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {
    @Override public void processPayment(ProcessPaymentRequest request,StreamObserver<ProcessPaymentResponse> response) {
        unavailable(response);
    }
    @Override public void refundPayment(RefundPaymentRequest request,StreamObserver<RefundPaymentResponse> response) {
        unavailable(response);
    }
    @Override public void getPaymentStatus(GetPaymentStatusRequest request,StreamObserver<GetPaymentStatusResponse> response) {
        unavailable(response);
    }
    private void unavailable(StreamObserver<?> response) {
        response.onError(Status.FAILED_PRECONDITION.withDescription("Use the authenticated payment API or durable Order commands").asRuntimeException());
    }
}