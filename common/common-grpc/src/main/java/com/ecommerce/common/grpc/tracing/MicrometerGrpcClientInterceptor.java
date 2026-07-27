package com.ecommerce.common.grpc.tracing;

import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/** Propagates the current Micrometer trace context over a gRPC client call. */
public final class MicrometerGrpcClientInterceptor implements ClientInterceptor {

    private static final Propagator.Setter<Metadata> METADATA_SETTER =
            (metadata, key, value) -> metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);

    private final Tracer tracer;
    private final Propagator propagator;

    public MicrometerGrpcClientInterceptor(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            io.grpc.CallOptions callOptions,
            io.grpc.Channel next
    ) {
        Span span = tracer.nextSpan()
                .name("grpc " + method.getFullMethodName())
                .remoteServiceName(next.authority())
                .start();
        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                propagator.inject(span.context(), headers, METADATA_SETTER);
                super.start(new ForwardingClientCallListener<>(responseListener, span, tracer), headers);
            }
        };
    }

    private static final class ForwardingClientCallListener<T>
            extends io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener<T> {

        private final Span span;
        private final Tracer tracer;

        private ForwardingClientCallListener(ClientCall.Listener<T> delegate, Span span, Tracer tracer) {
            super(delegate);
            this.span = span;
            this.tracer = tracer;
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                if (!status.isOk()) {
                    span.tag("grpc.status_code", status.getCode().name());
                }
                super.onClose(status, trailers);
            } catch (RuntimeException exception) {
                span.error(exception);
                throw exception;
            } finally {
                span.end();
            }
        }
    }
}
