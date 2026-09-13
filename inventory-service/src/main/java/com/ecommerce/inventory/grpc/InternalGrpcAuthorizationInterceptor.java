package com.ecommerce.inventory.grpc;

import io.grpc.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import javax.net.ssl.SSLSession;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/** Requires a trusted mTLS peer and an allow-listed internal caller in non-development deployments. */
@GrpcGlobalServerInterceptor
public class InternalGrpcAuthorizationInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> CALLER = Metadata.Key.of("x-internal-caller", Metadata.ASCII_STRING_MARSHALLER);
    private final boolean requireMtls;
    private final Set<String> callers;
    private final MeterRegistry meters;
    public InternalGrpcAuthorizationInterceptor(@Value("${inventory.grpc.require-mtls:false}") boolean requireMtls,
            @Value("${inventory.grpc.allowed-callers:order-service}") Set<String> callers, MeterRegistry meters) {
        this.requireMtls = requireMtls; this.callers = callers; this.meters = meters;
    }
    @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String caller = headers.get(CALLER); SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        if ((requireMtls && session == null) || caller == null || !callers.contains(caller)) {
            meters.counter("inventory_grpc_authorization_failures_total").increment();
            call.close(Status.PERMISSION_DENIED.withDescription("Internal caller is not authorized"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }
}
