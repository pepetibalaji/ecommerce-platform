package com.ecommerce.common.grpc.config;

import com.ecommerce.common.grpc.factory.GrpcClientFactory;
import com.ecommerce.common.grpc.tracing.MicrometerGrpcClientInterceptor;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(GrpcClientProperties.class)
public class GrpcClientAutoConfiguration {

    @Bean
    public GrpcClientFactory grpcClientFactory(
            GrpcClientProperties grpcClientProperties,
            Tracer tracer,
            Propagator propagator
    ) {
        return new GrpcClientFactory(
                grpcClientProperties,
                new MicrometerGrpcClientInterceptor(tracer, propagator)
        );
    }
}
