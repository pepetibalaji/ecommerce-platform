# Low-Level Design

`NotificationKafkaConsumer` consumes business and contact events. `NotificationEventService`
deduplicates events using `ProcessedEvent`; `RecipientDirectoryService` resolves recipients;
`NotificationDeliveryService` schedules provider delivery. JPA entities model notification,
recipient, preference, delivery, and processed-event state.
