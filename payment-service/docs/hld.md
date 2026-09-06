# Payment Service high-level design

## Ownership and flow

Payment Service owns payment-provider lifecycle, not orders. It stores amount/currency copied from Order Service and does not recalculate prices or inventory.

```text
Order Service -- order-created --> Kafka --> Payment Service --> PostgreSQL
Customer ---- checkout-session ----> provider adapter ---> provider checkout URL
Provider ---- signed webhook -------> Payment Service --> state + Kafka outcome
Order Service <--- payment success/failure/refund events --- Kafka
Admin ------- refund request -------> provider adapter
```

The active gateway is selected from configured Sandbox, Stripe, or Razorpay adapters. Payment records are created idempotently per order and checkout attempts are kept separately. Provider webhooks—not browser redirects—are authoritative.

## Boundaries

* Order Service owns order validity/status and consumes payment outcomes.
* Payment Service owns provider interaction, webhook dedupe, and refund lifecycle.
* No payment card or raw provider payload should be persisted/logged by application integrations beyond the verified processing path.
* Kafka outcome publication occurs after payment persistence but is not described as a transactional outbox guarantee in this module.
