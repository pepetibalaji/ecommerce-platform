# Notification Service high-level design

## Responsibility and flow

Notification Service receives asynchronous events, creates one local notification intent, and a scheduled worker delivers email later. The producer is decoupled from provider availability.

```text
Order / Payment / Inventory / Auth events -> Kafka -> Notification Service -> PostgreSQL
                                                             |
                                                             v
                                                    scheduled delivery worker
                                                             |
                                      recipient directory + preference + email provider
                                                             |
                                                  Logging / Mailtrap / SMTP
```

It owns recipient contact copies and preference/delivery history, not user identity or business state. Recipient contacts are updated from Auth's user-contact event. Auth identity-action events use an internal Auth delivery-token call so a raw action token is generated only for outbound email and is not persisted in notification payload.

## Event behavior

Business events map to notification types such as order received, payment successful/failed, cancellation, refund processed, shipment/delivery, low stock, and seller new order. Each event has an `eventId`; duplicate Kafka deliveries create no duplicate notification. Events without a direct recipient are ignored after being marked processed until an ownership resolver is added.
