import assert from "node:assert/strict";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const bundled = await build({
  stdin: {
    contents: 'export { api } from "./src/lib/api"; export { messageForError } from "./src/lib/format";',
    resolveDir: fileURLToPath(new URL("..", import.meta.url)), loader: "ts",
  },
  bundle: true, write: false, platform: "node", format: "esm",
  define: { "import.meta.env": JSON.stringify({ VITE_USE_MOCKS: "false", VITE_API_BASE_URL: "https://gateway.test" }) },
});
const { api, messageForError } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`);

test("nested service field errors reach the form message without envelope metadata", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({
    timestamp: "2026-09-10T10:00:00", status: 400, error: "Bad Request",
    path: "/api/v1/seller/products", message: "Request validation failed",
    fieldErrors: { price: "Must be positive", "requests[1].currency": "Unsupported currency", invalid: 7 },
  }, { status: 400 }));
  await assert.rejects(api.products.sellerCreate("test-access", { name: "Test", price: -1 }), error => {
    assert.deepEqual(error.fields, { price: "Must be positive", "requests[1].currency": "Unsupported currency" });
    assert.equal(messageForError(error), "Request validation failed price: Must be positive; requests[1].currency: Unsupported currency");
    return true;
  });
});

test("legacy plain validation maps remain supported", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({ name: "Name is required" }, { status: 400 }));
  await assert.rejects(api.products.list(), error => {
    assert.deepEqual(error.fields, { name: "Name is required" });
    return true;
  });
});

test("generic error envelopes are not mistaken for field validation", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({
    status: 503, error: "Service Unavailable", path: "/api/v1/products", message: "Please try again later",
  }, { status: 503 }));
  await assert.rejects(api.products.list(), error => {
    assert.equal(error.fields, undefined);
    assert.equal(messageForError(error), "Please try again later");
    return true;
  });
});

test("cart contention preserves the machine-readable code and retry delay", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({
    status: 409, error: "Conflict", message: "Cart is busy", code: "CART_LOCK_CONTENTION",
  }, { status: 409, headers: { "Retry-After": "2" } }));
  await assert.rejects(api.cart.addGuest("product-1", 1, "cart-operation-1"), error => {
    assert.equal(error.status, 409);
    assert.equal(error.code, "CART_LOCK_CONTENTION");
    assert.equal(error.retryAfter, 2);
    assert.equal(error.fields, undefined);
    assert.equal(messageForError(error), "Cart is busy");
    return true;
  });
});

test("structured checkout errors preserve server retryability, line details, and trace ID", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({
    code: "IDEMPOTENCY_KEY_REUSED",
    message: "This key belongs to a different checkout request.",
    retryable: false,
    details: [{ productId: "product-1", requestedQuantity: 2, availableQuantity: 1 }],
    traceId: "trace-checkout-123",
  }, { status: 409 }));

  await assert.rejects(
    api.orders.create("test-access", [{ productId: "product-1", quantity: 2 }], {
      recipientName: "Asha", phone: "+919999999999", line1: "10 Market Road", city: "Bengaluru",
      state: "Karnataka", postalCode: "560001", country: "IN",
    }, "INR", "checkout-key-1"),
    error => {
      assert.equal(error.code, "IDEMPOTENCY_KEY_REUSED");
      assert.equal(error.retryable, false);
      assert.deepEqual(error.details, [{ productId: "product-1", requestedQuantity: 2, availableQuantity: 1 }]);
      assert.equal(error.traceId, "trace-checkout-123");
      assert.equal(error.fields, undefined);
      return true;
    },
  );
});

test("admin refund requests use the audited Order Service command", async context => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    return Response.json({ id: "order-1", totalAmount: 999, currency: "INR", status: "REFUND_REQUESTED", items: [] });
  });

  const order = await api.orders.adminRequestRefund("admin-access", "order-1", "Customer cancellation approved");
  assert.equal(order.status, "REFUND_REQUESTED");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://gateway.test/api/v1/admin/orders/order-1/refund-requests");
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.headers.get("Authorization"), "Bearer admin-access");
  assert.equal(calls[0].options.body, JSON.stringify({ reason: "Customer cancellation approved" }));
});
