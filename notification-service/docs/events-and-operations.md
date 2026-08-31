# Events and Operations

Consumes order, payment, inventory, and `user-contact-updated` topics through Kafka listeners.
Configure PostgreSQL, Kafka, OAuth/JWK, and the selected email provider (logging, Mailtrap, or SMTP).
Monitor notification delivery exhaustion, consumer lag, provider error rate, and retry volume. Use
the failed-delivery admin API for investigation; never expose recipient data in public logs.
