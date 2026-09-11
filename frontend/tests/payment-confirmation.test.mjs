import assert from "node:assert/strict";
import test from "node:test";
import { pollPaymentConfirmation } from "../src/lib/payment-confirmation.ts";
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
test("refresh requests server verification, not another checkout or browser-supplied success", async context => {
  const api = await apiFor(false); const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({url, options});
    return Response.json({paymentId: "payment-1", orderId: "order-1", status: "SUCCESS", amount: 599, currency: "INR"});
  });
  const payment = await api.payments.refresh("fixture-token", "order-1");
  assert.equal(payment.id, "payment-1"); assert.equal(payment.status, "SUCCESS");
  assert.equal(calls.length, 1); assert.equal(calls[0].url, "https://gateway.test/api/v1/payments/orders/order-1/refresh");
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.headers.get("Authorization"), "Bearer fixture-token");
  assert.equal(calls[0].options.body, undefined);
});
test("mock refresh also preserves the recorded payment state", async () => {
  const api = await apiFor(true);
  const page = await api.payments.mine("fixture-token");
  const before = page.content[0];
  assert.equal((await api.payments.refresh("fixture-token", before.orderId)).status, before.status);
});
