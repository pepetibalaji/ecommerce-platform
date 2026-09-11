import assert from "node:assert/strict";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const bundled = await build({
  entryPoints: [fileURLToPath(new URL("../src/lib/api.ts", import.meta.url))],
  bundle: true,
  write: false,
  platform: "node",
  format: "esm",
  define: { "import.meta.env": JSON.stringify({ VITE_USE_MOCKS: "true" }) },
});
const { api, useMocks } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`);

test("mock catalogue returns products without optional price limits", async () => {
  assert.equal(useMocks, true);
  const page = await api.products.list();
  assert.ok(page.content.length > 0);
  assert.ok(page.totalElements > 0);
  assert.ok(page.content.every(product => product.active !== false));
});

test("mock catalogue ignores blank and whitespace-only price limits", async () => {
  const [unfiltered, blank] = await Promise.all([
    api.products.list(),
    api.products.list("minPrice=%20%20&maxPrice="),
  ]);
  assert.deepEqual(blank, unfiltered);
});

test("mock catalogue category filters work without price limits", async () => {
  const page = await api.products.list("category=Home");
  assert.ok(page.content.length > 0);
  assert.ok(page.content.every(product => product.category === "Home"));
});

test("mock catalogue respects an explicit maximum price of zero", async () => {
  const page = await api.products.list("maxPrice=0");
  assert.deepEqual(page.content, []);
  assert.equal(page.totalElements, 0);
});
