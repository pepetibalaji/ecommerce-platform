package com.ecommerce.order.grpc;

import com.ecommerce.proto.inventory.InventoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel inventoryChannel(
            @Value("${grpc.inventory.host:localhost}") String host,
            @Value("${grpc.inventory.port:9091}") int port
    ) {
        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceBlockingStub(
            ManagedChannel inventoryChannel
    ) {
        return InventoryServiceGrpc.newBlockingStub(inventoryChannel);
    }
}