package com.ecommerce.common.grpc.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc")
public class GrpcClientProperties {

    private Map<String, Client> clients = new HashMap<>();

    public Map<String, Client> getClients() {
        return clients;
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients == null ? new HashMap<>() : clients;
    }

    public Client getRequiredClient(String clientName) {
        Client client = clients.get(clientName);

        if (client == null) {
            throw new IllegalStateException(
                    "Missing gRPC client configuration for grpc.clients." + clientName
            );
        }

        client.validate(clientName);
        return client;
    }

    public static class Client {

        private String host = "localhost";
        private int port;
        private long deadlineMs = 3000;
        private boolean plaintext = true;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public long getDeadlineMs() {
            return deadlineMs;
        }

        public void setDeadlineMs(long deadlineMs) {
            this.deadlineMs = deadlineMs;
        }

        public boolean isPlaintext() {
            return plaintext;
        }

        public void setPlaintext(boolean plaintext) {
            this.plaintext = plaintext;
        }

        private void validate(String clientName) {
            if (host == null || host.isBlank()) {
                throw new IllegalStateException(
                        "Missing host for gRPC client: " + clientName
                );
            }

            if (port <= 0) {
                throw new IllegalStateException(
                        "Invalid port for gRPC client: " + clientName
                );
            }

            if (deadlineMs <= 0) {
                throw new IllegalStateException(
                        "Invalid deadline-ms for gRPC client: " + clientName
                );
            }
        }
    }
}