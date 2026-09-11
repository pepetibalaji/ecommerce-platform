import assert from "node:assert/strict";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

async function loadApi(mocks) {
  const bundled = await build({
    entryPoints: [fileURLToPath(new URL("../src/lib/api.ts", import.meta.url))],
    bundle: true, write: false, platform: "node", format: "esm",
    define: { "import.meta.env": JSON.stringify({ VITE_USE_MOCKS: String(mocks), VITE_API_BASE_URL: "https://gateway.test" }) },
  });
  return (await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`)).api;
}
const api = await loadApi(false);
const mockApi = await loadApi(true);

test("management detail reads use authenticated endpoints for hidden products", async (context) => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    return Response.json({ id: "hidden-product", name: "Hidden product", price: 10, active: false });
  });
  assert.equal((await api.products.sellerById("seller-access", "hidden-product")).active, false);
  assert.equal((await api.products.adminById("admin-access", "hidden-product")).active, false);
  assert.deepEqual(calls.map(call => call.url), [
    "https://gateway.test/api/v1/seller/products/hidden-product",
    "https://gateway.test/api/v1/admin/products/hidden-product",
  ]);
  assert.deepEqual(calls.map(call => call.options.headers.get("Authorization")), ["Bearer seller-access", "Bearer admin-access"]);
  assert.ok(calls.every(call => call.options.credentials === "include"));
});

test("archive actions retain the backend DELETE contract without a response body", async (context) => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    return new Response(null, { status: 204 });
  });
  await api.products.sellerArchive("seller-access", "product-id");
  await api.products.adminArchive("admin-access", "product-id");
  assert.deepEqual(calls.map(call => call.options.method), ["DELETE", "DELETE"]);
  assert.ok(calls[0].url.endsWith("/seller/products/product-id"));
  assert.ok(calls[1].url.endsWith("/admin/products/product-id"));
});

test("recovery requests use bounded authenticated cursor batches and explicit replay", async (context) => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith("replay-dead-letters")) return new Response(null, { status: 204 });
    return Response.json({ enqueued: 100, nextAfterId: "next-product" });
  });
  assert.equal((await api.products.reconcile("admin-access")).nextAfterId, "next-product");
  await api.products.reconcile("admin-access", "next-product");
  await api.products.replayDeadLetters("admin-access");
  assert.ok(calls[0].url.endsWith("/outbox/reconcile?size=100"));
  assert.ok(calls[1].url.endsWith("/outbox/reconcile?size=100&afterId=next-product"));
  assert.ok(calls[2].url.endsWith("/outbox/replay-dead-letters"));
  assert.ok(calls.every(call => call.options.method === "POST" && call.options.headers.get("Authorization") === "Bearer admin-access"));
});

test("mock bulk imports can be archived, read privately, and reactivated", async () => {
  const draft = { name: "Lifecycle contract product", price: 50, currency: "INR", imageUrls: [] };
  const [product] = await mockApi.products.sellerBulkCreate("mock-seller-token", [draft]);
  await mockApi.products.sellerArchive("mock-seller-token", product.id);
  await assert.rejects(mockApi.products.byId(product.id), error => error.status === 404);
  assert.equal((await mockApi.products.sellerById("mock-seller-token", product.id)).active, false);
  assert.equal((await mockApi.products.adminById("mock-admin-token", product.id)).active, false);
  await mockApi.products.sellerUpdate("mock-seller-token", product.id, { ...draft, active: true });
  assert.equal((await mockApi.products.byId(product.id)).active, true);
  await mockApi.products.replayDeadLetters("mock-admin-token");
  assert.ok((await mockApi.products.reconcile("mock-admin-token")).enqueued > 0);
});
