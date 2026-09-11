import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { build } from "esbuild";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Routes, Route } from "react-router-dom";

// Read-only markup checks. No authenticated backend actions are executed.
const fixture = { resources: [], loaders: [], index: 0, loading: false, error: null, roles: ["ADMIN"] };
const mocks = {
  AuthProvider: 'export const useAuth = () => ({user:{id:"viewer",name:"Workspace Owner",email:"owner@example.com",roles:globalThis.fixture.roles,permissions:[]},accessToken:"test-only",hasAnyRole:(...roles)=>roles.some(role=>globalThis.fixture.roles.includes(role)),hasPermission:()=>false,logout:async()=>{}});',
  hooks: "export const useResource = (load) => {globalThis.fixture.loaders.push(load);return {data:globalThis.fixture.resources[globalThis.fixture.index++] ?? null,loading:globalThis.fixture.loading,error:globalThis.fixture.error,reload:async()=>{},setData:()=>{}};};",
  api: "export const api = {products:{sellerById:(...args)=>globalThis.fixture.sellerById(...args)}};",
  useSellerProducts: "export const useSellerProducts = () => ({products:globalThis.fixture.resources[0]?.content ?? [],totalElements:globalThis.fixture.resources[0]?.totalElements ?? 0,loading:globalThis.fixture.loading,error:globalThis.fixture.error,loadingMore:false,loadMoreError:null,hasMore:true,loadMore:()=>{},retry:()=>{},reload:async()=>{},sentinelRef:{current:null},...globalThis.fixture.stream});",
};
const bundled = await build({
  stdin: {
    contents: 'export * from "./src/pages/backoffice"; export {BackofficeLayout} from "./src/components/BackofficeExperience";',
    resolveDir: fileURLToPath(new URL("..", import.meta.url)), loader: "tsx",
  },
  bundle: true, write: false, platform: "node", format: "cjs", packages: "external",
  jsx: "automatic", loader: { ".css": "empty" },
  plugins: [{ name: "workspace-fixtures", setup(builder) {
    builder.onResolve({ filter: /\/(AuthProvider|hooks|api|useSellerProducts)$/ }, args => ({ path: args.path.split("/").at(-1), namespace: "fixture" }));
    builder.onLoad({ filter: /.*/, namespace: "fixture" }, args => ({ contents: mocks[args.path], loader: "js" }));
  } }],
});
const module = { exports: {} };
runInNewContext(bundled.outputFiles[0].text, { module, exports: module.exports, require: createRequire(import.meta.url), fixture, URLSearchParams, console });
const pages = module.exports;
const h = React.createElement;
const product = { id: "product-1234", name: "Canvas Bag", category: "Accessories", price: 999, currency: "INR", active: true };
const order = { id: "order-123456", status: "PENDING", totalAmount: 999, sellerTotalAmount: 999, currency: "INR", items: [{ productId: product.id, quantity: 1, price: 999 }] };
const page = (content, total = content.length) => ({ content, totalElements: total, totalPages: Math.ceil(total / 10), number: 0, size: 10 });
function render(name, area, resources = [], options = {}) {
  Object.assign(fixture, { resources, loaders: [], index: 0, loading: false, error: null, stream: {}, roles: [area === "seller" ? "SELLER" : "ADMIN"] }, options);
  return renderToStaticMarkup(h(MemoryRouter, { initialEntries: [options.entry ?? "/" + area] }, h(Routes, null,
    h(Route, { element: h(pages.BackofficeLayout, { area }) },
      h(Route, { path: options.path ?? "/" + area, element: h(pages[name], options.props) })))));
}

test("seller overview shows real loaded records, scope note and useful next actions", () => {
  const html = render("SellerOverviewPage", "seller", [{ products: page([product], 42), orders: page([order], 17) }]);
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.ok(html.includes("first 100 loaded records"));
  assert.ok(html.includes('href="/seller/products/product-1234/edit"'));
  assert.ok(html.includes('href="/seller/inventory"'));
  assert.ok(!html.includes('href="/admin"'));
  assert.ok(html.includes('aria-label="Open workspace navigation"'));
});

test("admin overview links all six operational workspaces without fake charts", () => {
  const html = render("AdminOverviewPage", "admin");
  for (const path of ["users", "catalogue", "inventory", "orders", "payments", "notifications"]) assert.ok(html.includes('href="/admin/' + path + '"'));
  assert.equal((html.match(/class="bo-module-icon"/g) || []).length, 6);
  assert.equal((html.match(/<h1\b/g) || []).length, 1);
  assert.ok(!html.includes("Revenue today"));
});

test("seller catalogue uses infinite loading and retains edit/stock links and role-limited bulk import", () => {
  const html = render("SellerProductsPage", "seller", [page([product], 42)]);
  assert.ok(html.includes("Search loaded products"));
  assert.ok(html.includes("1 loaded / 42 total"));
  assert.ok(!html.includes('aria-label="Pagination"'));
  assert.ok(html.includes("More products load automatically as you scroll."));
  assert.ok(html.includes(">Load more products</button>"));
  assert.ok(html.includes('href="/seller/products/import"'));
  assert.ok(html.includes("Stock"));
  assert.ok(html.includes(">Archive</button>"));
  assert.ok(!html.includes(">Delete</button>"));
  const admin = render("SellerProductsPage", "seller", [page([product])], { roles: ["ADMIN"] });
  assert.ok(!admin.includes('href="/seller/products/import"'));
});

test("seller next-batch failure preserves existing rows and retry controls", () => {
  const html = render("SellerProductsPage", "seller", [page([product], 42)], { stream: { loadMoreError: "Unavailable" } });
  assert.ok(html.includes("Canvas Bag"));
  assert.ok(html.includes(">Retry loading</button>"));
  assert.ok(!html.includes("No seller products yet"));
});

test("seller infinite list stops loading at the final batch", () => {
  const html = render("SellerProductsPage", "seller", [page([product])], { stream: { hasMore: false } });
  assert.ok(html.includes("All products loaded."));
  assert.ok(!html.includes(">Load more products</button>"));
});

test("admin users retain their account links, roles and status", () => {
  const html = render("AdminUsersPage", "admin", [page([{ id: "user-1234", name: "Example Seller", email: "seller@example.com", role: "SELLER", status: "ACTIVE" }])]);
  assert.ok(html.includes('href="/admin/users/user-1234"'));
  assert.ok(html.includes("Example Seller"));
  assert.ok(html.includes("SELLER"));
  assert.ok(html.includes("Search this page"));
});

test("order management retains supported transitions only", () => {
  const html = render("AdminOrdersPage", "admin", [page([order, { ...order, id: "closed-order", status: "REFUNDED" }])]);
  assert.ok(html.includes('<option value="CONFIRMED"'));
  assert.ok(html.includes('<option value="CANCELLED"'));
  assert.ok(!html.includes('<option value="SHIPPED"'));
  assert.equal((html.match(/>Apply<\/button>/g) || []).length, 1);
});

test("seller queue stays read-only and address details stay collapsed", () => {
  const html = render("SellerOrdersPage", "seller", [page([{ ...order, shippingAddress: { recipientName: "Recipient", line1: "Example street", city: "Chennai", state: "TN", postalCode: "600001", country: "IN" } }])]);
  assert.ok(html.includes('<details class="fulfilment-address">'));
  assert.ok(!html.includes('<details class="fulfilment-address" open'));
  assert.ok(!html.includes(">Apply</button>"));
  assert.ok(!html.includes(">Request refund</button>"));
});

test("payment list requires selection before showing refund controls", () => {
  const html = render("AdminPaymentsPage", "admin", [page([{ id: "payment-123", orderId: order.id, amount: 999, currency: "INR", status: "SUCCESS" }]), null]);
  assert.ok(html.includes(">Inspect</button>"));
  assert.ok(html.includes("Payment detail"));
  assert.ok(!html.includes(">Request refund</button>"));
});

test("notification diagnostics preserve redaction and replay confirmation", () => {
  const html = render("AdminNotificationsPage", "admin", [[{ id: "notification-123", status: "FAILED", type: "EMAIL", recipient: "private@example.com", message: "private-message-body" }]]);
  assert.ok(html.includes("Redacted"));
  assert.ok(!html.includes("private@example.com"));
  assert.ok(!html.includes("private-message-body"));
  assert.ok(html.includes(">Replay Auth dead letters</button>"));
  assert.ok(!html.includes(">Replay now</button>"));
});

test("product creation has grouped fields, draft preview and HTTPS image guidance", () => {
  const html = render("AdminCataloguePage", "admin");
  assert.ok(html.includes("Product essentials"));
  assert.ok(html.includes("Pricing &amp; visibility"));
  assert.ok(html.includes('aria-label="Product preview"'));
  assert.ok(html.includes("approved HTTPS image URL"));
  assert.ok(html.includes("Seller ID"));
  assert.ok(html.includes(">Replay Product dead letters</button>"));
  assert.ok(html.includes(">Reconcile catalogue</button>"));
  assert.ok(!html.includes(">Confirm replay</button>"));
  assert.ok(!html.includes(">Queue batch</button>"));
});

test("seller editor directly retrieves its product including records beyond the first list page", async () => {
  const calls = [];
  const hidden = { ...product, active: false };
  fixture.sellerById = async (...args) => { calls.push(args); return hidden; };
  render("SellerProductEditorPage", "seller", [hidden], {
    entry: "/seller/products/product-1234/edit", path: "/seller/products/:productId/edit",
  });
  assert.equal(await fixture.loaders[0](), hidden);
  assert.deepEqual(calls, [["test-only", "product-1234"]]);
});

test("admin editor exposes management retrieval and requires loading before archival", () => {
  const html = render("AdminCataloguePage", "admin", [], { props: { mode: "edit" } });
  assert.ok(html.includes(">Load product</button>"));
  assert.ok(!html.includes("Load active public data"));
  assert.ok(html.includes("Load the product to review its current details"));
  assert.ok(html.includes('disabled="" type="button">Archive product</button>') || /<button[^>]*disabled[^>]*>Archive product<\/button>/.test(html));
});

test("loading and failures do not claim an empty product catalogue", () => {
  const loading = render("SellerProductsPage", "seller", [], { loading: true });
  assert.ok(loading.includes("Loading products"));
  assert.ok(!loading.includes("No seller products yet"));
  const failed = render("SellerProductsPage", "seller", [], { error: "Unavailable" });
  assert.ok(failed.includes("Unavailable"));
  assert.ok(!failed.includes("No seller products yet"));
});
