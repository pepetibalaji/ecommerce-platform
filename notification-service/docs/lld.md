# Notification Service low-level design

## Components

| Component | Role |
| --- | --- |
| `NotificationKafkaConsumer` | Consumes business, contact, and auth-action Kafka topics. |
| `NotificationEventService` | JSON parsing, `ProcessedEvent` uniqueness dedupe, type/recipient resolution, preferences. |
| `RecipientDirectoryService` | Maintains user/email/active recipient records. |
| `NotificationDeliveryService` | Polls PENDING intents, stores attempts, sends email, applies retry/failure status. |
| Email providers | Configured Logging, Mailtrap API, or Mailtrap/SMTP delivery adapter. |
| `AuthDeliveryTokenClient` | Obtains token at send time for verification/reset/email-change links. |

## Delivery algorithm

Every 5 seconds by default the worker reads up to 100 PENDING notifications oldest-first. It skips an item if its latest attempt's `nextAttemptAt` is in the future. It creates the next attempt, resolves an active recipient email (or embedded auth-action delivery email), then invokes the provider.

Provider success stores message ID, marks delivery SENT and notification SENT. Failure stores a sanitized/truncated error. Before max attempts it keeps notification PENDING and schedules randomized exponential delay (base delay doubled per attempt, capped at one hour); at max attempts it marks notification FAILED and increments `notification_delivery_exhausted_total`.

`ProcessedEvent` is inserted before recipient/type work. Therefore malformed/no-recipient events are considered processed and are not automatically retried from Kafka. Delivery is at-least-once at worker level; no database lock/claim is visible around selecting PENDING records, so deployment concurrency should be assessed before running multiple workers.
