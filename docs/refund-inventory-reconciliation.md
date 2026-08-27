# Refund inventory reconciliation

`payment-refund-completed` is emitted only after a provider reports a refund as
successful. Its `eventId` is the payment-refund UUID, so it can be safely
republished and Order Service stores it in `order_processed_events` before a
duplicate can enqueue a second release.

| Refund / order state | Order action | Inventory action |
| --- | --- | --- |
| Partial successful refund, `CONFIRMED` | `PARTIALLY_REFUNDED` | Keep reservation. |
| Full successful refund, `CONFIRMED` or `PARTIALLY_REFUNDED` | `REFUNDED` | Insert one `FULL_REFUND` release-outbox command per reservation. |
| Full refund for any other order state | `REFUND_REQUIRES_FULFILMENT_REVIEW` | Do not enqueue a release. |
| Release reaches an already deducted reservation | Outbox becomes `MANUAL_REVIEW` | No stock mutation occurs; fulfilment owns the remedy. |

## Manual path

An operator must review `REFUND_REQUIRES_FULFILMENT_REVIEW` orders and
`MANUAL_REVIEW` rows in `order_inventory_release_outbox` together with the
shipment/fulfilment record. If stock was deducted or shipped, do **not** replay
the release command. Arrange a return, replacement, or finance adjustment as
appropriate, then close the fulfilment case. If inventory was not deducted,
repair the reservation record and retry/recreate the release command using its
existing reservation ID; Inventory Service's reservation-aware release is
idempotent.
