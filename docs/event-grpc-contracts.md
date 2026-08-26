# Event and gRPC Contract Specification

**Status:** Current contracts and versioning rules  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Contract rules

- Kafka delivery is at least once; every consumer must be idempotent.
- Events use `orderId` as key where order ordering matters.
- Event payloads include a unique `eventId`, correlation ID, and trace ID.
- Producers add fields compatibly; consumers ignore unknown fields. Removing or
  changing the meaning/type of a field requires a new event version/topic.
- gRPC/protobuf field numbers are permanent: never reuse or renumber them.

## 2. Kafka topics

| Topic | Producer | Consumer | Key | Current purpose |
| --- | --- | --- | --- | --- |
| `order-created` | Order Service | Payment Service | `orderId` | Idempotently create one pending payment. |
| `payment-success` | Payment Service | Order Service | `orderId` | Confirm pending order. |
| `payment-failed` | Payment Service | Order Service | `orderId` | Fail pending order and queue inventory release. |
| `order-dlq` | Order error handler | Operations/replay process | Original key | Terminal payment-outcome processing failure. |

The local bootstrap creates each business topic with three partitions and
replication factor one. Production partitioning/replication requires capacity
and availability sizing.

## 3. Event semantics

| Event | Required meaning | Consumer outcome |
| --- | --- | --- |
| `OrderCreatedEvent` | A persisted `PENDING` order with user, amount, currency, and items is ready for payment preparation. | Create/reuse one payment identified by order ID. |
| `PaymentSuccessEvent` | Provider-verified successful payment for an order. | If order is `PENDING`, change to `CONFIRMED`; ignore late/duplicate terminal outcomes. |
| `PaymentFailedEvent` | Provider-verified failure or cancellation for an order. | If order is `PENDING`, change to `PAYMENT_FAILED` and insert one release command per reservation. |

Consumers must validate event type/shape. Unknown order or infrastructure error
retries three times in Order Service then enters `order-dlq`; malformed outcome
is non-retryable and sent to the DLQ.

## 4. gRPC contracts

### Inventory Service

| RPC | Request | Response | Caller | Rule |
| --- | --- | --- | --- | --- |
| `GetInventory` | `productId` | product ID, available stock, reserved stock | Order | Read stock. |
| `ReserveStock` | `productId`, quantity, `reservationId` | success/message | Order | Create/reuse a reservation. |
| `ReleaseStock` | `productId`, quantity, `reservationId` | success/message | Order worker | Idempotently return reserved stock. |
| `DeductStock` | `productId`, quantity, `reservationId` | success/message | Future fulfilment | Idempotently consume reservation. |

Invalid/missing inventory maps to gRPC `NOT_FOUND`; validation and business
preconditions map to `FAILED_PRECONDITION`. New callers must supply a stable
reservation ID; legacy quantity-only behavior is not safely deduplicable.

### Payment and Shipping Services

| Service | RPC | Status |
| --- | --- | --- |
| Payment | `ProcessPayment`, `RefundPayment`, `GetPaymentStatus` | Defined and served; not part of active Order flow. |
| Shipping | `AssignShipment`, `UpdateShipmentStatus`, `GetShipment` | Protobuf contract exists; service is planned. |

## 5. Reliability and operations

- Order uses processed-event inbox uniqueness for payment event deduplication.
- Payment uses provider event uniqueness for webhook deduplication.
- Order release outbox retries Inventory gRPC indefinitely after payment failure
  or cancellation.
- Current Order and Payment Kafka producer sends are not transactional. Future
  outboxes must publish in retryable order and provide replay-safe identifiers.
- Track consumer lag, DLQ records, retries, duplicate events, and release work.

## 6. Contract ownership

`common/common-proto/src/main/proto/` owns protobuf contracts and
`common/common-events` owns shared event models/topic constants. Contract
changes require producer/consumer compatibility review and automated contract
tests before release.
