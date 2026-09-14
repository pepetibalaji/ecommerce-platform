import assert from "node:assert/strict";
import test from "node:test";
import { pollPaymentConfirmation, approvedCheckoutUrl, paymentPreparationDelay } from "../src/lib/payment-confirmation.ts";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

test("confirmation keeps checking beyond the old five attempts and stops on success", async () => {
  let calls = 0; let exhausted = false;
  await pollPaymentConfirmation(async () => ({ terminal: ++calls === 8 }), () => false, () => {}, () => { exhausted = true; }, async () => {});
  assert.equal(calls, 8); assert.equal(exhausted, false);
});
test("pending checks are bounded and a fresh run can restart after exhaustion", async () => {
  let calls = 0; let elapsed = 0; let stopped = false;
  await pollPaymentConfirmation(async () => { calls++; return { terminal: false }; }, () => false, () => {}, () => { stopped = true; }, async ms => { elapsed += ms; });
  assert.equal(calls, 25); assert.equal(elapsed, 117000); assert.equal(stopped, true);
  await pollPaymentConfirmation(async () => { calls++; return { terminal: true }; }, () => false, () => {}, () => assert.fail("should not exhaust"), async () => {});
  assert.equal(calls, 26);
});
test("unmount or navigation cancels later requests and progress updates", async () => {
  let cancelled = false; let calls = 0;
  await pollPaymentConfirmation(async () => { calls++; cancelled = true; return { terminal: false }; }, () => cancelled,
    () => assert.fail("stale progress"), () => assert.fail("stale exhausted state"), async () => assert.fail("stale timer"));
  assert.equal(calls, 1);
});
test("terminal authorization errors stop rather than polling forever", async () => {
  let calls = 0;
  await pollPaymentConfirmation(async () => { calls++; return { terminal: true }; }, () => false, () => assert.fail(), () => assert.fail(), async () => assert.fail());
  assert.equal(calls, 1);
});

async function apiFor(mocks) {
  const result = await build({entryPoints: [fileURLToPath(new URL("../src/lib/api.ts", import.meta.url))],
    bundle: true, write: false, platform: "node", format: "esm",
    define: {"import.meta.env": JSON.stringify({VITE_API_BASE_URL: "https://gateway.test", VITE_USE_MOCKS: String(mocks)})}});
  return (await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString("base64")}`)).api;
}
test("refresh queries recorded state without triggering provider confirmation", async context => {
  const api = await apiFor(false); const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({url, options});
    return Response.json({paymentId: "payment-1", orderId: "order-1", status: "SUCCESS", amount: 599, currency: "INR"});
  });
  const payment = await api.payments.refresh("fixture-token", "order-1");
  assert.equal(payment.id, "payment-1"); assert.equal(payment.status, "SUCCESS");
  assert.equal(calls.length, 1); assert.equal(calls[0].url, "https://gateway.test/api/v1/payments/orders/order-1");
  assert.equal(calls[0].options.method, undefined);
  assert.equal(calls[0].options.headers.get("Authorization"), "Bearer fixture-token");
  assert.equal(calls[0].options.body, undefined);
});
test("mock refresh also preserves the recorded payment state", async () => {
  const api = await apiFor(true);
  const page = await api.payments.mine("fixture-token");
  const before = page.content[0];
  assert.equal((await api.payments.refresh("fixture-token", before.orderId)).status, before.status);
});

test("checkout redirect requires exact approved host and an unexpired UTC-aware timestamp", () => {
  const now = Date.parse("2026-09-14T10:00:00Z"); const expiresAt = "2026-09-14T10:30:00Z";
  assert.equal(approvedCheckoutUrl("https://checkout.stripe.com/c/pay/test", expiresAt, { now }), "https://checkout.stripe.com/c/pay/test");
  for (const url of ["https://checkout.stripe.com.evil.test/pay", "http://checkout.stripe.com/pay", "https://user@checkout.stripe.com/pay", "https://checkout.stripe.com:8443/pay"]) {
    assert.throws(() => approvedCheckoutUrl(url, expiresAt, { now }));
  }
  for (const invalid of [undefined, "2026-09-14T10:30:00", "2026-09-14T09:00:00Z", "invalidZ"]) {
    assert.throws(() => approvedCheckoutUrl("https://checkout.stripe.com/pay", invalid, { now }));
  }
});
test("sandbox redirect is allowed only by the explicit development caller flag", () => {
  const url = "http://localhost:3001/mock-checkout?paymentId=test"; const expiresAt = "2099-01-01T00:00:00Z";
  assert.throws(() => approvedCheckoutUrl(url, expiresAt));
  assert.equal(approvedCheckoutUrl(url, expiresAt, { sandbox: true }), url);
  assert.throws(() => approvedCheckoutUrl("http://localhost:3001/other", expiresAt, { sandbox: true }));
});
test("preparation retries only the stable preparation code and are bounded", () => {
  const preparing = { code: "PAYMENT_PREPARING", retryable: true, retryAfter: 2 };
  assert.equal(paymentPreparationDelay(preparing, 0), 2000);
  assert.equal(paymentPreparationDelay(preparing, 4), null);
  assert.equal(paymentPreparationDelay({ ...preparing, retryAfter: 100 }, 1), 5000);
  assert.equal(paymentPreparationDelay({ code: "PAYMENT_NOT_OWNED", retryable: false }, 0), null);
  assert.equal(paymentPreparationDelay({ retryable: true }, 0), null);
});
test("mock checkout redirect does not manufacture a success outcome", async () => {
  const api = await apiFor(true); const page = await api.payments.mine("fixture-token");
  const session = await api.payments.checkoutSession("fixture-token", page.content[0].orderId);
  assert.equal(session.status, "REQUIRES_CUSTOMER_ACTION");
  assert.ok(Date.parse(session.expiresAt) > Date.now());
});
