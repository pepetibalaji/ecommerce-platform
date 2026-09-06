# Notification Service current implementation

## Implemented

* Kafka-driven durable email notification intents with processed-event deduplication.
* Local recipient directory from user-contact events and preference-based SKIPPED behavior.
* Order/payment/refund/shipment/inventory/seller and Auth identity-action topic consumers.
* Logging, Mailtrap, and SMTP-oriented provider adapters; attempt history and randomized backoff retry.
* Admin failed/delivery visibility and user-scoped notification/preference APIs.
* Auth action URLs generated only at send time through an internal delivery-token client.

## Important limitations

* No producer outbox or consumer retry/DLT policy is shown in this module.
* No admin retry/requeue endpoint exists for exhausted failures.
* API returns persistence entities directly rather than versioned DTOs.
* Events lacking a recipient are marked processed then ignored; no later ownership resolution occurs.
* Multi-instance worker coordination/claiming is not implemented in the visible delivery query.
* SMS, push, in-app inbox, and several upstream event producers remain outside current implementation.
