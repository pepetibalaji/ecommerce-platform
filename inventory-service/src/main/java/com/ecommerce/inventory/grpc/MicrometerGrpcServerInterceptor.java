package com.ecommerce.inventory.grpc;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/** Continues W3C trace context received from another service over gRPC. */
@GrpcGlobalServerInterceptor
public class MicrometerGrpcServerInterceptor implements ServerInterceptor {

    private static final Propagator.Getter<Metadata> METADATA_GETTER =
            (metadata, key) -> metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

    private final Tracer tracer;
    private final Propagator propagator;

    public MicrometerGrpcServerInterceptor(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        Span span = propagator.extract(headers, METADATA_GETTER)
                .name("grpc " + call.getMethodDescriptor().getFullMethodName())
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
            return new TracingServerCallListener<>(listener, span, tracer);
        } catch (RuntimeException exception) {
            span.error(exception);
            span.end();
            throw exception;
        }
    }

    private static final class TracingServerCallListener<T>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<T> {

        private final Span span;
        private final Tracer tracer;

        private TracingServerCallListener(ServerCall.Listener<T> delegate, Span span, Tracer tracer) {
            super(delegate);
            this.span = span;
            this.tracer = tracer;
        }

        @Override
        public void onComplete() {
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                super.onComplete();
            } finally {
                span.end();
            }
        }

        @Override
        public void onCancel() {
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                span.tag("grpc.status_code", Status.Code.CANCELLED.name());
                super.onCancel();
            } finally {
                span.end();
            }
        }
    }
}
