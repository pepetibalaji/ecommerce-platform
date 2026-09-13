import assert from "node:assert/strict";
import test from "node:test";
import { InfinitePageController } from "../src/lib/infinite-page.ts";

function stream() {
  const requests = [];
  const controller = new InfinitePageController(number => new Promise((resolve, reject) => requests.push({ number, resolve, reject })));
  return { controller, requests };
}
const page = (ids, number = 0, totalPages = 3) => ({ content: ids.map(id => ({ id })), number, totalPages, totalElements: 6, size: 2, last: number + 1 >= totalPages });

test("observer bursts and clicks append each operational batch once and retain earlier rows", async () => {
  const { controller, requests } = stream();
  const first = controller.start();
  await Promise.all([controller.autoLoadMore(), controller.loadMore()]);
  assert.equal(requests.length, 1);
  requests[0].resolve(page(["a", "b", "b"])); await first;
  const next = controller.autoLoadMore();
  await Promise.all([controller.autoLoadMore(), controller.loadMore()]);
  assert.equal(requests.length, 2);
  assert.equal(requests[1].number, 1);
  assert.equal(controller.getSnapshot().loading, false);
  assert.equal(controller.getSnapshot().loadingMore, true);
  assert.deepEqual(controller.getSnapshot().items.map(row => row.id), ["a", "b"]);
  requests[1].resolve(page(["b", "c", "c"], 1)); await next;
  assert.deepEqual(controller.getSnapshot().items.map(row => row.id), ["a", "b", "c"]);
});

test("failed next batch retains records and explicitly retries the same page", async () => {
  const { controller, requests } = stream();
  const first = controller.start(); requests[0].resolve(page(["a"])); await first;
  const next = controller.autoLoadMore(); requests[1].reject(new Error("Connection lost")); await next;
  assert.deepEqual(controller.getSnapshot().items, [{ id: "a" }]);
  assert.equal(controller.getSnapshot().error, null);
  assert.equal(controller.getSnapshot().loadMoreError, "Connection lost");
  await Promise.all([controller.autoLoadMore(), controller.autoLoadMore()]);
  assert.equal(requests.length, 2);
  const retry = controller.retry(); assert.equal(requests[2].number, 1);
  requests[2].resolve(page(["b"], 1)); await retry;
  assert.deepEqual(controller.getSnapshot().items.map(row => row.id), ["a", "b"]);
  assert.equal(controller.getSnapshot().loadMoreError, null);
});

test("refresh rejects stale successes and stale failures cannot release the new request lock", async () => {
  const { controller, requests } = stream();
  const old = controller.start();
  const current = controller.start();
  requests[0].reject(new Error("Outdated error")); await old;
  await controller.autoLoadMore();
  assert.equal(requests.length, 2);
  assert.equal(controller.getSnapshot().loading, true);
  assert.equal(controller.getSnapshot().error, null);
  requests[1].resolve(page(["current"])); await current;
  const staleBatch = controller.loadMore();
  const refresh = controller.start();
  requests[2].resolve(page(["stale"], 1)); await staleBatch;
  requests[3].resolve(page(["fresh"], 0, 1)); await refresh;
  assert.deepEqual(controller.getSnapshot().items, [{ id: "fresh" }]);
  assert.equal(requests[3].number, 0);
});

test("initial failures pause automatic requests and unmount discards late results", async () => {
  const { controller, requests } = stream();
  const first = controller.start(); requests[0].reject(new Error("Unavailable")); await first;
  assert.equal(controller.getSnapshot().error, "Unavailable");
  await controller.autoLoadMore(); assert.equal(requests.length, 1);
  const retry = controller.retry(); assert.equal(requests[1].number, 0);
  controller.dispose(); requests[1].resolve(page(["late"])); await retry;
  assert.deepEqual(controller.getSnapshot().items, []);
  const restart = controller.start(); requests[2].resolve(page(["valid"], 0, 1)); await restart;
  assert.deepEqual(controller.getSnapshot().items, [{ id: "valid" }]);
});

test("all batches remain reachable and last or empty responses stop loading", async () => {
  const { controller, requests } = stream();
  for (let number = 0; number < 3; number++) {
    const loading = number === 0 ? controller.start() : controller.autoLoadMore();
    assert.equal(requests[number].number, number);
    requests[number].resolve(page([String(number * 2), String(number * 2 + 1)], number)); await loading;
  }
  assert.equal(controller.getSnapshot().items.length, 6);
  assert.equal(controller.getSnapshot().hasMore, false);
  await controller.loadMore(); assert.equal(requests.length, 3);
  const refresh = controller.start(); requests[3].resolve(page([])); await refresh;
  assert.equal(controller.getSnapshot().hasMore, false);
  await controller.autoLoadMore(); assert.equal(requests.length, 4);
});
