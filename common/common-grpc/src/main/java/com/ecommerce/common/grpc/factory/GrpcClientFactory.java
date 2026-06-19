package com.ecommerce.common.grpc.factory;

import com.ecommerce.common.grpc.config.GrpcClientProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class GrpcClientFactory {

    private final GrpcClientProperties properties;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public GrpcClientFactory(GrpcClientProperties properties) {
        this.properties = properties;
    }

    public ManagedChannel channel(String clientName) {
        return channels.computeIfAbsent(clientName, this::createChannel);
    }

    public <T extends AbstractStub<T>> T stub(
            String clientName,
            Function<ManagedChannel, T> stubFactory
    ) {
        T stub = stubFactory.apply(channel(clientName));
        return withDeadline(clientName, stub);
    }

    public <T extends AbstractStub<T>> T withDeadline(
            String clientName,
            T stub
    ) {
        GrpcClientProperties.Client client =
                properties.getRequiredClient(clientName);

        return stub.withDeadlineAfter(
                client.getDeadlineMs(),
                TimeUnit.MILLISECONDS
        );
    }

    private ManagedChannel createChannel(String clientName) {
        GrpcClientProperties.Client client =
                properties.getRequiredClient(clientName);

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                .forAddress(client.getHost(), client.getPort());

        if (client.isPlaintext()) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }

        return builder.build();
    }

    @PreDestroy
    public void shutdown() {
        for (ManagedChannel channel : channels.values()) {
            shutdownChannel(channel);
        }
    }

    private void shutdownChannel(ManagedChannel channel) {
        if (channel.isShutdown()) {
            return;
        }

        channel.shutdown();

        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException exception) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}