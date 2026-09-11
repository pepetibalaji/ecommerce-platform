# Payment confirmation and recovery

The browser returning from Stripe is not proof of payment. Payment becomes `SUCCESS` only from a verified provider webhook or an authenticated server-to-Stripe lookup of the checkout session already stored against that customer's payment attempt.

## Changes

- Gateway permits anonymous POST only to the Stripe and Razorpay webhook paths. Payment Service still verifies provider signatures; customer payment and refresh APIs still require authentication.
- Stripe Checkout immediate and delayed completion events are supported. Checkout events from an incompatible webhook API version are verified using an authenticated session retrieval rather than silently ignored.
- `POST /api/v1/payments/orders/{orderId}/refresh` verifies ownership, locks the payment row, and checks its saved Stripe session. It never creates a checkout or charges a card. Active payments are checked at most once per five seconds per payment; confirmed/refund states are returned without a provider request.
- Late checkout events cannot downgrade `SUCCESS` or overwrite refund states. Repeated confirmations do not emit another completion outcome. Provider outages return a safe 503 and leave the recorded result unchanged.
- The frontend checks for up to about two minutes, cancels stale polling on navigation, and restarts checks with Refresh status. Success is never inferred from query parameters. A pending return shows Awaiting confirmation instead of asking the customer to pay again.
- Legacy `/public/payments/success` and `/cancel` redirect to the frontend verification page instead of displaying an unverified success/cancellation message.

## Deployment

1. Deploy the updated Payment Service, Gateway, frontend, and config repository. Payment startup applies Flyway V3 (`last_provider_check_at`). No existing payment record is marked paid by the migration.
2. Set `PAYMENT_CHECKOUT_SUCCESS_URL` and `PAYMENT_CHECKOUT_CANCEL_URL` to your frontend `/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}`. Dev defaults use port 5173. Existing explicit environment overrides must also be corrected.
3. Set `PAYMENT_FRONTEND_RETURN_URL` to the frontend `/payment/return` URL without query parameters. This is required in stage/prod and also handles sessions created before this update.
4. Configure Stripe to deliver `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`, and `checkout.session.expired` to `/api/v1/payments/webhooks/stripe` on the externally reachable Gateway. Configure the matching endpoint signing secret as `STRIPE_WEBHOOK_SECRET` in Payment Service. Test and live credentials/events must match.

For local development, forward real test-mode events with the Stripe CLI:

```powershell
stripe listen --forward-to http://localhost:8080/api/v1/payments/webhooks/stripe
```

Use the signing secret printed by that listener in the local Payment Service configuration; do not commit it. Restart Payment Service after changing that secret. The CLI listener must remain running. See [Stripe fulfillment](https://docs.stripe.com/checkout/fulfillment) and [webhook setup](https://docs.stripe.com/webhooks).

For an already-paid but stale order, sign in as its owner and open its Check payment status page after deployment. Refresh status retrieves the existing session. Do not create another checkout to recover status. Failed/cancelled payments are not automatically resurrected by this refresh endpoint; delayed settlement after an order has been cancelled requires an operational review.

## Verification

```powershell
mvn -pl payment-service,gateway-service -am test
npm --prefix frontend test
npm --prefix frontend run build
```

Payment tests use a disposable PostgreSQL database for migrations, persisted recovery, concurrent refreshes, ownership, throttling, provider-outage rollback, and late/duplicate webhook behavior. Stripe adapter tests verify signed fixture events and mock authenticated SDK retrieval; no real card is charged. Gateway tests verify webhook routing authorization; HTTP tests cover safe return redirects and 503 responses. Frontend tests cover bounded/cancelled/restarted polling and the authenticated refresh request.

This does not replace the separate reliability work needed for Payment-to-Order Kafka publication: the existing publisher remains asynchronous and is not a transactional outbox. A confirmed payment and an order awaiting its Kafka outcome are different states; this change must not be described as a redesign of that delivery mechanism.
