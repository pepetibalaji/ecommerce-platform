# High-Level Design

Inventory Service owns stock counters and reservation state in PostgreSQL. It exposes admin and
seller REST APIs, Inventory gRPC for Order Service, and Kafka provisioning from Product Service.
