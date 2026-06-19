package com.ecommerce.common.grpc.config;

import com.ecommerce.common.grpc.factory.GrpcClientFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(GrpcClientProperties.class)
public class GrpcClientAutoConfiguration {

    @Bean
    public GrpcClientFactory grpcClientFactory(GrpcClientProperties grpcClientProperties) {
        return new GrpcClientFactory(grpcClientProperties);
    }
}