import assert from "node:assert/strict";
import test from "node:test";
import { setImmediate } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";

const bundled = await build({
  stdin: {
    contents: 'export { CartRequestController, retryCartLockContention } from "./src/cart/cart-requests"; export { ApiError } from "./src/domain";',
    resolveDir: fileURLToPath(new URL("..", import.meta.url)), loader: "ts",
  },
  bundle: true, write: false, platform: "node", format: "esm",
});
const { CartRequestController, retryCartLockContention, ApiError } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString("base64")}`);

const guest = { key: "guest", accessToken: null };
const customer = { key: "customer:a", accessToken: "token-a" };
const contention = (retryAfter) => new ApiError("Cart is busy", 409, { code: "CART_LOCK_CONTENTION", retryAfter });

function cart(ownerId = "guest", version = 1) {
  return { ownerType: ownerId === "guest" ? "GUEST" : "CUSTOMER", ownerId, items: [], version, updatedAt: "2026-09-11T00:00:00Z" };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function setup(overrides = {}) {
  const calls = [];
  const pauses = [];
  let nextKey = 0;
  const requests = Object.fromEntries([
    "guest", "customer", "addGuest", "addCustomer", "updateGuest", "updateCustomer",
    "removeGuest", "removeCustomer", "mergeGuest",
  ].map(name => [name, async (...args) => {
    calls.push({ name, args });
    return overrides[name] ? overrides[name](...args) : cart(name === "guest" || name.endsWith("Guest") && name !== "mergeGuest" ? "guest" : "a");
  }]));
  const controller = new CartRequestController(requests, () => `request-${++nextKey}`, async ms => { pauses.push(ms); });
  return { controller, calls, pauses };
}

test("authentication pending makes no requests and repeated guest initialization shares the initial read", async () => {
  const read = deferred();
  const { controller, calls } = setup({ guest: () => read.promise });
  controller.setSession(null);
  await setImmediate();
  assert.equal(calls.length, 0);
  assert.equal(controller.getSnapshot().isLoading, true);

  controller.setSession(guest);
  controller.setSession({ ...guest });
  const refresh = controller.refresh();
  const duplicateRefresh = controller.refresh();
  await setImmediate();
  assert.deepEqual(calls.map(call => call.name), ["guest"]);
  read.resolve(cart());
  await Promise.all([refresh, duplicateRefresh]);
  assert.equal(controller.getSnapshot().ownerKey, "guest");
  assert.deepEqual(controller.getSnapshot().cart, cart());
  assert.equal(controller.getSnapshot().isLoading, false);
});

test("customer bootstrap deduplicates merges and serializes a requested refresh after the merge", async () => {
  const merge = deferred();
  const { controller, calls } = setup({ mergeGuest: () => merge.promise, customer: () => cart("a", 2) });
  controller.setSession(customer);
  controller.setSession({ ...customer });
  const firstMerge = controller.mergeGuest();
  const secondMerge = controller.mergeGuest();
  const refresh = controller.refresh();
  const secondRefresh = controller.refresh();
  await setImmediate();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest"]);

  merge.resolve(cart("a"));
  await Promise.all([firstMerge, secondMerge, refresh, secondRefresh]);
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer"]);
  assert.equal(controller.getSnapshot().mergeState, "complete");
  assert.equal(controller.getSnapshot().cart.version, 2);
});

test("a rotated access token does not bootstrap again and is used by subsequent requests", async () => {
  const { controller, calls } = setup();
  controller.setSession(customer);
  await controller.mergeGuest();
  controller.setSession({ ...customer, accessToken: "rotated-token" });
  await controller.refresh();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer", "customer"]);
  assert.deepEqual(calls[2].args, ["rotated-token"]);
});

test("add, update, remove, and refresh serialize behind a pending merge", async () => {
  const merge = deferred();
  const add = deferred();
  const { controller, calls } = setup({ mergeGuest: () => merge.promise, addCustomer: () => add.promise });
  controller.setSession(customer);
  const adding = controller.add("product-1", 2);
  const updating = controller.update("item-1", 3);
  const removing = controller.remove("item-1");
  await setImmediate();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest"]);

  merge.resolve(cart("a"));
  await setImmediate();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer", "addCustomer"]);
  add.resolve(cart("a", 2));
  await Promise.all([adding, updating, removing]);
  await controller.refresh();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer", "addCustomer", "updateCustomer", "removeCustomer", "customer"]);
  assert.deepEqual(calls[3].args, ["token-a", "item-1", 3]);
  assert.deepEqual(calls[4].args, ["token-a", "item-1"]);
});

test("switching accounts cancels queued work and ignores the old account's late response", async () => {
  const oldMerge = deferred();
  const newMerge = deferred();
  const { controller, calls } = setup({
    mergeGuest: token => token === "token-a" ? oldMerge.promise : newMerge.promise,
    customer: () => cart("b"),
  });
  controller.setSession(customer);
  const oldCompletion = controller.mergeGuest();
  const queuedAdd = controller.add("product-1", 1);
  await setImmediate();

  controller.setSession({ key: "customer:b", accessToken: "token-b" });
  const newCompletion = controller.mergeGuest();
  assert.equal(controller.getSnapshot().ownerKey, "customer:b");
  assert.equal(controller.getSnapshot().cart, null);
  await setImmediate();
  assert.deepEqual(calls.map(call => call.args[0]), ["token-a", "token-b"]);
  newMerge.resolve(cart("b"));
  await newCompletion;
  oldMerge.resolve(cart("a", 99));
  await Promise.all([oldCompletion, queuedAdd]);
  assert.equal(controller.getSnapshot().cart.ownerId, "b");
  assert.equal(controller.getSnapshot().cart.version, 1);
  assert.equal(controller.getSnapshot().error, null);
  assert.equal(calls.some(call => call.name === "addCustomer"), false);
  assert.deepEqual(calls.map(call => call.args[0]), ["token-a", "token-b", "token-b"]);
});

test("an old account's failed merge cannot trigger a fallback read or replace the new snapshot", async () => {
  const oldMerge = deferred();
  const { controller, calls } = setup({
    mergeGuest: token => token === "token-a" ? oldMerge.promise : cart("b"),
    customer: () => cart("b"),
  });
  controller.setSession(customer);
  const oldCompletion = controller.mergeGuest().catch(() => undefined);
  await setImmediate();
  controller.setSession({ key: "customer:b", accessToken: "token-b" });
  await controller.mergeGuest();
  oldMerge.reject(new ApiError("Old account failed", 503));
  await oldCompletion;
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "mergeGuest", "customer"]);
  assert.equal(controller.getSnapshot().cart.ownerId, "b");
  assert.equal(controller.getSnapshot().error, null);
});

test("lock contention retries at most three times with bounded Retry-After delays", async () => {
  let attempts = 0;
  const delays = [];
  const result = await retryCartLockContention(async () => {
    attempts += 1;
    if (attempts < 3) throw contention(attempts === 1 ? undefined : 20);
    return "saved";
  }, async delay => { delays.push(delay); });
  assert.equal(result, "saved");
  assert.equal(attempts, 3);
  assert.deepEqual(delays, [1000, 3000]);

  attempts = 0;
  delays.length = 0;
  await assert.rejects(retryCartLockContention(async () => {
    attempts += 1;
    throw contention(2);
  }, async delay => { delays.push(delay); }), error => error.code === "CART_LOCK_CONTENTION");
  assert.equal(attempts, 3);
  assert.deepEqual(delays, [2000, 2000]);
});

test("business conflicts, other statuses, and ordinary errors are never retried", async () => {
  for (const failure of [
    new ApiError("Idempotency key was reused", 409, { code: "IDEMPOTENCY_CONFLICT" }),
    new ApiError("Conflict", 409),
    new ApiError("Unavailable", 503, { code: "CART_LOCK_CONTENTION" }),
    new ApiError("Offline", 0),
    new Error("CART_LOCK_CONTENTION"),
  ]) {
    let attempts = 0;
    const delays = [];
    await assert.rejects(retryCartLockContention(async () => {
      attempts += 1;
      throw failure;
    }, async delay => { delays.push(delay); }), error => error === failure);
    assert.equal(attempts, 1);
    assert.deepEqual(delays, []);
  }
});

test("retrying stops when the session becomes stale during its delay", async () => {
  let current = true;
  let attempts = 0;
  const result = await retryCartLockContention(async () => {
    attempts += 1;
    throw contention();
  }, async () => { current = false; }, () => current);
  assert.equal(attempts, 1);
  assert.equal(result, undefined);
});

test("add and merge reuse their original idempotency keys across contention retries", async () => {
  let mergeAttempts = 0;
  let addAttempts = 0;
  const { controller, calls } = setup({
    mergeGuest: () => { if (++mergeAttempts < 3) throw contention(); return cart("a"); },
    addCustomer: () => { if (++addAttempts < 3) throw contention(); return cart("a", 2); },
  });
  controller.setSession(customer);
  await controller.mergeGuest();
  await controller.add("product-1", 2);
  const merges = calls.filter(call => call.name === "mergeGuest");
  const additions = calls.filter(call => call.name === "addCustomer");
  assert.equal(merges.length, 3);
  assert.equal(additions.length, 3);
  assert.equal(new Set(merges.map(call => call.args[1])).size, 1);
  assert.equal(new Set(additions.map(call => call.args[3])).size, 1);
  assert.notEqual(merges[0].args[1], additions[0].args[3]);
  assert.deepEqual(additions[0].args.slice(0, 3), ["token-a", "product-1", 2]);
});

test("update and remove report contention without automatic retries", async () => {
  const { controller, calls, pauses } = setup({
    updateGuest: () => { throw contention(); },
    removeGuest: () => { throw contention(); },
  });
  controller.setSession(guest);
  await controller.refresh();
  await assert.rejects(controller.update("item-1", 2), error => error.code === "CART_LOCK_CONTENTION");
  await assert.rejects(controller.remove("item-1"), error => error.code === "CART_LOCK_CONTENTION");
  assert.deepEqual(calls.map(call => call.name), ["guest", "updateGuest", "removeGuest"]);
  assert.deepEqual(pauses, []);
});

test("a failed merge reads the customer cart and a manual retry preserves the merge key", async () => {
  let attempts = 0;
  const { controller, calls } = setup({
    mergeGuest: () => { if (++attempts === 1) throw new ApiError("Merge unavailable", 503); return cart("a", 1); },
    customer: () => cart("a", attempts === 1 ? 2 : 3),
  });
  controller.setSession(customer);
  await controller.mergeGuest().catch(() => undefined);
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer"]);
  assert.equal(controller.getSnapshot().cart.version, 2);
  assert.equal(controller.getSnapshot().mergeState, "retry");
  assert.equal(controller.getSnapshot().error, null);

  await controller.mergeGuest();
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer", "mergeGuest", "customer"]);
  assert.equal(calls[0].args[1], calls[2].args[1]);
  assert.equal(controller.getSnapshot().cart.version, 3);
  assert.equal(controller.getSnapshot().mergeState, "complete");
  assert.equal(controller.getSnapshot().error, null);
});

test("a missing guest cart finishes merging after reading the customer snapshot", async () => {
  const { controller, calls } = setup({ mergeGuest: () => { throw new ApiError("Guest cart not found", 400); } });
  controller.setSession(customer);
  await controller.mergeGuest().catch(() => undefined);
  assert.deepEqual(calls.map(call => call.name), ["mergeGuest", "customer"]);
  assert.equal(controller.getSnapshot().cart.ownerId, "a");
  assert.equal(controller.getSnapshot().mergeState, "complete");
  assert.equal(controller.getSnapshot().error, null);
});

test("a successful refresh clears an earlier request error", async () => {
  let reads = 0;
  const { controller } = setup({ guest: () => {
    if (++reads === 1) throw new ApiError("Cart unavailable", 503);
    return cart("guest", 2);
  } });
  controller.setSession(guest);
  await controller.refresh().catch(() => undefined);
  assert.ok(controller.getSnapshot().error);
  await controller.refresh();
  assert.equal(controller.getSnapshot().cart.version, 2);
  assert.equal(controller.getSnapshot().error, null);
});
