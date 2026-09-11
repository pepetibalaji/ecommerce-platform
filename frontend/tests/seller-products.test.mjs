import assert from "node:assert/strict";
import test from "node:test";
import { build } from "esbuild";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";

const fixture = { calls: [], page: { content: [{ id: "owned-product", name: "Owned product", price: 99 }], page: { number: 1, size: 12, totalElements: 25, totalPages: 3 } } };
const modules = {
  react: "export const useCallback = callback => callback;",
  api: "export const api = {products:{sellerList:async(token,query)=>{globalThis.fixture.calls.push({token,query});return globalThis.fixture.page;}}};",
  useInfiniteProducts: "export const useInfiniteProducts = (query,fetchPage)=>({query,fetchPage});",
};
const bundled = await build({
  entryPoints: [fileURLToPath(new URL("../src/lib/useSellerProducts.ts", import.meta.url))],
  bundle: true, write: false, platform: "node", format: "cjs",
  plugins: [{ name: "seller-hook-fixtures", setup(builder) {
    builder.onResolve({ filter: /^(react|\.\/api|\.\/useInfiniteProducts)$/ }, args => ({ path: args.path.replace("./", ""), namespace: "fixture" }));
    builder.onLoad({ filter: /.*/, namespace: "fixture" }, args => ({ contents: modules[args.path], loader: "js" }));
  } }],
});
const module = { exports: {} };
runInNewContext(bundled.outputFiles[0].text, { module, exports: module.exports, fixture });
const { useSellerProducts } = module.exports;

test("seller infinite batches use the authenticated seller endpoint and normalize page metadata", async () => {
  fixture.calls = [];
  const stream = useSellerProducts("seller-session");
  const page = await stream.fetchPage("page=1&size=12");
  assert.equal(stream.query, "");
  assert.equal(fixture.calls[0].token, "seller-session");
  assert.equal(fixture.calls[0].query, "page=1&size=12");
  assert.equal(page.content[0].id, "owned-product");
  assert.equal(page.totalElements, 25);
  assert.equal(page.number, 1);
});

test("a missing seller session never falls back to public product requests", async () => {
  fixture.calls = [];
  await assert.rejects(useSellerProducts(null).fetchPage("page=0&size=12"), /session has ended/);
  assert.equal(fixture.calls.length, 0);
});

test("a new seller session binds its own credential to subsequent batches", async () => {
  fixture.calls = [];
  await useSellerProducts("session-one").fetchPage("page=0&size=12");
  await useSellerProducts("session-two").fetchPage("page=0&size=12");
  assert.deepEqual(fixture.calls.map(call => call.token), ["session-one", "session-two"]);
});
