import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { build } from "esbuild";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Routes, Route } from "react-router-dom";

// Markup regression tests, not a replacement for browser interaction/visual QA.
const product = {
  id: "render-product", name: "Everyday Canvas Tote", category: "Accessories",
  brand: "Pepekart", price: 999, currency: "INR", active: true,
  description: "An everyday carry.",
  imageUrls: [
    "https://cdn.dummyjson.com/product-images/womens-bags/heshe-women's-leather-bag/thumbnail.webp",
    "https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp",
  ],
};
const base = {
  products: [product], totalElements: 100, loading: false, loadingMore: false,
  error: null, loadMoreError: null, hasMore: true,
  loadMore: () => {}, retry: () => {}, sentinelRef: { current: null },
};
const fixture = { stream: base, product };
const mocks = {
  AuthProvider: "export const useAuth = () => ({user:null,isAuthenticated:false,hasAnyRole:()=>false,logout:async()=>{}});",
  CartProvider: "export const useCart = () => ({cart:{items:[{quantity:2}]},add:async()=>{}});",
  api: 'export const api = {products:{facets:()=>({categories:[{name:"Home",count:25},{name:"Accessories",count:25}],brands:[]}),byId:()=>globalThis.fixture.product}};',
  hooks: "export const useResource = load => ({data:load(),loading:false,error:null,reload:()=>{}});",
  useInfiniteProducts: "export const useInfiniteProducts = () => globalThis.fixture.stream;",
};
const bundled = await build({
  stdin: {
    contents: 'export {CataloguePage} from "./src/pages/CatalogueExperience"; export {ProductDetailPage} from "./src/pages/ProductDetailExperience"; export {StorefrontLayout} from "./src/components/layouts";',
    resolveDir: fileURLToPath(new URL("..", import.meta.url)), loader: "tsx",
  },
  bundle: true, write: false, platform: "node", format: "cjs",
  packages: "external", jsx: "automatic", loader: { ".css": "empty" },
  plugins: [{
    name: "render-fixtures",
    setup(builder) {
      builder.onResolve({ filter: /\/(AuthProvider|CartProvider|api|hooks|useInfiniteProducts)$/ },
        args => ({ path: args.path.split("/").at(-1), namespace: "fixture" }));
      builder.onLoad({ filter: /.*/, namespace: "fixture" },
        args => ({ contents: mocks[args.path], loader: "js" }));
    },
  }],
});
const module = { exports: {} };
runInNewContext(bundled.outputFiles[0].text, {
  module, exports: module.exports, require: createRequire(import.meta.url),
  URLSearchParams, fixture, console,
});
const { CataloguePage, ProductDetailPage, StorefrontLayout } = module.exports;
const h = React.createElement;
function render(path = "/", stream = {}, selectedProduct = product) {
  fixture.stream = { ...base, ...stream };
  fixture.product = selectedProduct;
  return renderToStaticMarkup(h(MemoryRouter, { initialEntries: [path] },
    h(Routes, null, h(Route, { element: h(StorefrontLayout) },
      h(Route, { path: "/", element: h(CataloguePage) }),
      h(Route, { path: "/products/:productId", element: h(ProductDetailPage) })))));
}

test("home renders one h1, product links, accessible shell, and no page navigation", () => {
  const html = render();
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.ok(html.includes("Good things."));
  assert.ok(html.includes('href="/products/render-product"'));
  assert.ok(html.includes("Shopping bag, 2 items"));
  assert.ok(html.includes('aria-label="Sign in"'));
  assert.ok(!html.includes('aria-label="Pagination"'));
});

test("filtered catalogue keeps a page h1 and removes the homepage hero", () => {
  const html = render("/?category=Home");
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.ok(html.includes('<h1 id="shop-catalogue-title">Home</h1>'));
  assert.ok(!html.includes("shop-hero-title"));
});

test("initial loading renders skeletons without an empty-result message", () => {
  const html = render("/", { products: [], loading: true });
  assert.ok(html.includes("shop-skeleton"));
  assert.ok(!html.includes("Nothing here just yet."));
});

test("initial failure offers retry without claiming an empty collection", () => {
  const html = render("/", { products: [], error: "Network unavailable" });
  assert.ok(html.includes("Try again"));
  assert.ok(!html.includes("Nothing here just yet."));
});

test("an empty result offers a route back to all products", () => {
  const html = render("/", { products: [], totalElements: 0, hasMore: false });
  assert.ok(html.includes("Nothing here just yet."));
  assert.ok(html.includes("Explore all products"));
});

test("a later failure keeps loaded cards visible and exposes retry", () => {
  const html = render("/", { loadMoreError: "Network unavailable" });
  assert.ok(html.includes(product.name));
  assert.ok(html.includes("Retry loading"));
});

test("the final batch shows completion instead of another load control", () => {
  const html = render("/", { hasMore: false, totalElements: 1 });
  assert.ok(!html.includes("Load more products"));
  assert.ok(html.includes("shop-end-message"));
});

test("product detail renders its gallery and disables unavailable purchases", () => {
  const html = render("/products/render-product");
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.ok(html.includes("Show photo 2 of Everyday Canvas Tote"));
  assert.ok(html.includes("Add to bag"));
  const unavailable = render("/products/render-product", {}, { ...product, active: false });
  assert.ok(unavailable.includes("Currently unavailable"));
  assert.match(unavailable, /<button[^>]*pd-add-button[^>]*disabled=""/);
});
