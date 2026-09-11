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
