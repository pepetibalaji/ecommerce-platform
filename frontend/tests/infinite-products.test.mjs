import assert from "node:assert/strict";
import test from "node:test";
import { InfiniteProductsController, productPageQuery } from "../src/lib/infinite-products.ts";
import { toPage } from "../src/lib/format.ts";

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function stream(query = "category=Home&sort=price_asc") {
  const requests = [];
  const controller = new InfiniteProductsController(query, (params) => {
    const pending = deferred();
    requests.push({ params: new URLSearchParams(params), ...pending });
    return pending.promise.then(toPage);
  });
  return { controller, requests };
}

function page(ids, number = 0, totalElements = 30, totalPages = 3) {
  return {
    content: ids.map((id) => ({ id, name: `Product ${id}`, price: 100 })),
    page: { number, size: 12, totalElements, totalPages },
  };
}

test("refresh after a deletion restarts page zero and discards an in-flight old batch", async () => {
  const { controller, requests } = stream("");
  const first = controller.start();
  requests[0].resolve(page(["removed", "remaining"]));
  await first;
  const oldBatch = controller.loadMore();
  const refreshed = controller.start();
  assert.equal(requests[2].params.get("page"), "0");
  assert.equal(controller.getSnapshot().products.length, 0);
  requests[1].resolve(page(["stale"], 1));
  await oldBatch;
  requests[2].resolve(page(["remaining", "next"], 0, 2, 1));
  await refreshed;
  assert.deepEqual(controller.getSnapshot().products.map(product => product.id), ["remaining", "next"]);
  assert.equal(controller.getSnapshot().hasMore, false);
});

test("page requests preserve filters and own page/size parameters", () => {
  const params = new URLSearchParams(productPageQuery("q=linen+shirt&category=Apparel&page=8&size=100&sort=name_asc", 2));
  assert.equal(params.get("q"), "linen shirt");
  assert.equal(params.get("category"), "Apparel");
  assert.equal(params.get("sort"), "name_asc");
  assert.deepEqual(params.getAll("page"), ["2"]);
  assert.deepEqual(params.getAll("size"), ["12"]);
});

test("observer bursts request pages sequentially and append unique products", async () => {
  const { controller, requests } = stream();
  const first = controller.start();
  await Promise.all([controller.autoLoadMore(), controller.autoLoadMore(), controller.loadMore()]);
  assert.equal(requests.length, 1);
  requests[0].resolve(page(["a", "b", "b"]));
  await first;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["a", "b"]);
  assert.equal(controller.getSnapshot().totalElements, 30);

  const next = controller.autoLoadMore();
  await controller.autoLoadMore();
  assert.equal(requests.length, 2);
  assert.equal(requests[1].params.get("page"), "1");
  assert.equal(requests[1].params.get("category"), "Home");
  assert.equal(controller.getSnapshot().loading, false);
  assert.equal(controller.getSnapshot().loadingMore, true);
  requests[1].resolve(page(["b", "c", "c"], 1));
  await next;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["a", "b", "c"]);
});

test("filter reset starts at page zero and ignores an older successful request", async () => {
  const { controller, requests } = stream();
  const old = controller.start();
  const current = controller.start("category=Stationery&sort=newest");
  assert.equal(requests[1].params.get("page"), "0");
  assert.equal(requests[1].params.get("category"), "Stationery");
  requests[1].resolve(page(["new"], 0, 1, 1));
  await current;
  requests[0].resolve(page(["old"]));
  await old;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["new"]);
  assert.equal(controller.getSnapshot().totalElements, 1);
});

test("late failures after reset cannot replace current data or its loading state", async () => {
  const { controller, requests } = stream();
  const old = controller.start();
  const current = controller.start("category=Apparel");
  requests[0].reject(new Error("Old query failed"));
  await old;
  assert.equal(controller.getSnapshot().loading, true);
  assert.equal(controller.getSnapshot().error, null);
  await controller.autoLoadMore();
  assert.equal(requests.length, 2, "stale cleanup must not release the current request lock");
  requests[1].resolve(page(["shirt"], 0, 1, 1));
  await current;
});

test("disposing ignores late responses and supports the React StrictMode restart", async () => {
  const { controller, requests } = stream();
  const old = controller.start();
  controller.dispose();
  requests[0].resolve(page(["discarded"]));
  await old;
  assert.deepEqual(controller.getSnapshot().products, []);
  await controller.autoLoadMore();
  assert.equal(requests.length, 1);
  const restarted = controller.start();
  requests[1].resolve(page(["current"], 0, 1, 1));
  await restarted;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["current"]);
});

test("all 100 products are reachable, and the last page stops requests", async () => {
  const ids = Array.from({ length: 100 }, (_, index) => `product-${index}`);
  const { controller, requests } = stream();
  for (let number = 0; number < 9; number += 1) {
    const pending = number === 0 ? controller.start() : controller.autoLoadMore();
    assert.equal(requests[number].params.get("page"), String(number));
    requests[number].resolve(page(ids.slice(number * 12, (number + 1) * 12), number, 100, 9));
    await pending;
  }
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ids);
  assert.equal(controller.getSnapshot().hasMore, false);
  await Promise.all([controller.autoLoadMore(), controller.loadMore(), controller.retry()]);
  assert.equal(requests.length, 9);
});

test("empty responses stop the stream even if stale metadata claims more pages", async () => {
  const { controller, requests } = stream();
  const first = controller.start();
  requests[0].resolve(page([]));
  await first;
  assert.equal(controller.getSnapshot().hasMore, false);
  await controller.autoLoadMore();
  assert.equal(requests.length, 1);
});

test("initial failure pauses automatic loading until an explicit retry", async () => {
  const { controller, requests } = stream();
  const first = controller.start();
  requests[0].reject(new Error("Temporarily unavailable"));
  await first;
  assert.equal(controller.getSnapshot().error, "Temporarily unavailable");
  assert.equal(controller.getSnapshot().loading, false);
  await Promise.all([controller.autoLoadMore(), controller.autoLoadMore()]);
  assert.equal(requests.length, 1);
  const retry = controller.retry();
  assert.equal(requests[1].params.get("page"), "0");
  requests[1].resolve(page(["a"]));
  await retry;
  assert.equal(controller.getSnapshot().error, null);
  assert.equal(controller.getSnapshot().hasMore, true);
});

test("next-page failures preserve products and retry the same page without loops", async () => {
  const { controller, requests } = stream();
  const first = controller.start();
  requests[0].resolve(page(["a"]));
  await first;
  const next = controller.autoLoadMore();
  requests[1].reject(new Error("Connection lost"));
  await next;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["a"]);
  assert.equal(controller.getSnapshot().error, null);
  assert.equal(controller.getSnapshot().loadMoreError, "Connection lost");
  await Promise.all([controller.autoLoadMore(), controller.autoLoadMore()]);
  assert.equal(requests.length, 2);
  const retry = controller.retry();
  assert.equal(requests[2].params.get("page"), "1");
  requests[2].resolve(page(["b"], 1));
  await retry;
  assert.deepEqual(controller.getSnapshot().products.map((item) => item.id), ["a", "b"]);
  assert.equal(controller.getSnapshot().loadMoreError, null);
  const final = controller.autoLoadMore();
  requests[3].resolve(page(["c"], 2));
  await final;
  assert.equal(controller.getSnapshot().hasMore, false);
});
