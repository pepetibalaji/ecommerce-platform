package com.ecommerce.product.grpc;

import io.grpc.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import javax.net.ssl.SSLSession;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/** Enforces Inventory-only access to Product reconciliation snapshots. */
@GrpcGlobalServerInterceptor
public class InternalProductGrpcAuthorizationInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> CALLER = Metadata.Key.of("x-internal-caller", Metadata.ASCII_STRING_MARSHALLER);
    private final boolean requireMtls; private final Set<String> callers; private final MeterRegistry meters;
    public InternalProductGrpcAuthorizationInterceptor(@Value("${product.grpc.require-mtls:false}") boolean requireMtls,
            @Value("${product.grpc.allowed-callers:inventory-service}") Set<String> callers, MeterRegistry meters) {
        this.requireMtls=requireMtls; this.callers=callers; this.meters=meters;
    }
    @Override public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT,RespT> call, Metadata headers, ServerCallHandler<ReqT,RespT> next) {
        SSLSession tls=call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION); String caller=headers.get(CALLER);
        if ((requireMtls && tls==null) || caller==null || !callers.contains(caller)) { meters.counter("product_grpc_authorization_failures_total").increment(); call.close(Status.PERMISSION_DENIED.withDescription("Internal caller is not authorized"),new Metadata()); return new ServerCall.Listener<>(){}; }
        return next.startCall(call,headers);
    }
}
