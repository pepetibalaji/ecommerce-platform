import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { build } from "esbuild";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";

const fixture = { cart: null, error: null, mergeState: "idle" };
const mocks = {
  AuthProvider: "export const useAuth = () => ({isAuthenticated:true});",
  CartProvider: "export const useCart = () => ({...globalThis.fixture,isLoading:false,refresh:async()=>{},mergeGuest:async()=>{},update:async()=>{},remove:async()=>{}});",
  api: "export const api = {}; export const useMocks = false;",
  hooks: "export const useResource = () => ({data:[],loading:false,error:null});",
};
const bundled = await build({
  stdin: { contents: 'export {CartPage} from "./src/pages/storefront";', resolveDir: fileURLToPath(new URL("..", import.meta.url)), loader: "tsx" },
  bundle: true, write: false, platform: "node", format: "cjs", packages: "external", jsx: "automatic", loader: { ".css": "empty" },
  define: { "import.meta.env": "{}" },
  plugins: [{ name: "cart-fixtures", setup(builder) {
    builder.onResolve({ filter: /\/(AuthProvider|CartProvider|api|hooks)$/ }, args => ({ path: args.path.split("/").at(-1), namespace: "fixture" }));
    builder.onLoad({ filter: /.*/, namespace: "fixture" }, args => ({ contents: mocks[args.path], loader: "js" }));
  } }],
});
const module = { exports: {} };
runInNewContext(bundled.outputFiles[0].text, { module, exports: module.exports, require: createRequire(import.meta.url), fixture, console });

function render(state) {
  Object.assign(fixture, { cart: null, error: null, mergeState: "idle" }, state);
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(module.exports.CartPage)));
}

test("a failed cart load does not claim the cart is empty", () => {
  const html = render({ error: "Cart storage is temporarily unavailable." });
  assert.ok(html.includes("Unable to load this page"));
  assert.ok(html.includes("Try again"));
  assert.ok(!html.includes("Your cart is empty"));
});

test("a successfully loaded empty snapshot can display the empty state", () => {
  const html = render({ cart: { items: [] } });
  assert.ok(html.includes("Your cart is empty"));
  assert.ok(!html.includes("Unable to load this page"));
});

test("merge recovery presents an explicit merge retry alongside the current cart", () => {
  const html = render({ mergeState: "retry", cart: { items: [{ itemId: "line-1", productId: "product-1", quantity: 2 }] } });
  assert.ok(html.includes("Cart merge needs attention"));
  assert.ok(html.includes(">Retry merge</button>"));
  assert.ok(html.includes("Order summary"));
  assert.ok(!html.includes("Unable to load this page"));
  assert.ok(!html.includes("Your cart is empty"));
});
