import assert from "node:assert/strict";
import test from "node:test";
import { toPage } from "../src/lib/format.ts";

test("Spring Data DTO metadata keeps all 100 products reachable across nine pages", () => {
  const products = Array.from({ length: 100 }, (_, index) => ({ id: index + 1 }));
  const visited = [];
  let nextPage = 0;

  while (true) {
    const page = toPage({
      content: products.slice(nextPage * 12, (nextPage + 1) * 12),
      page: { size: 12, number: nextPage, totalElements: 100, totalPages: 9 },
    });

    assert.equal(page.totalElements, 100);
    assert.equal(page.totalPages, 9);
    assert.equal(page.size, 12);
    assert.equal(page.number, nextPage);
    assert.equal(page.first, nextPage === 0);
    assert.equal(page.last, nextPage === 8);
    visited.push(...page.content);
    if (page.last) break;
    nextPage = page.number + 1;
    assert.ok(nextPage < 9, "pagination must terminate after the last page");
  }

  assert.deepEqual(visited, products);
});

test("empty Spring Data DTO pages preserve zero totals", () => {
  assert.deepEqual(toPage({
    content: [],
    page: { size: 12, number: 0, totalElements: 0, totalPages: 0 },
  }), {
    content: [], totalElements: 0, totalPages: 0, number: 0, size: 12, first: true, last: true,
  });
});

test("live catalogue response keeps its 180 products and 15 pages", () => {
  for (const number of [0, 1]) {
    const content = Array.from({ length: 12 }, (_, index) => ({ id: number * 12 + index + 1 }));
    assert.deepEqual(toPage({
      content,
      page: { size: 12, number, totalElements: 180, totalPages: 15 },
    }), {
      content, totalElements: 180, totalPages: 15, number, size: 12, first: number === 0, last: false,
    });
  }
});

test("legacy flat pages retain their counts and explicit navigation flags", () => {
  const legacyPage = {
    content: [{ id: "product-13" }],
    totalElements: 100,
    totalPages: 9,
    number: 1,
    size: 12,
    first: false,
    last: false,
  };
  assert.deepEqual(toPage(legacyPage), legacyPage);
});

test("arrays remain a single page", () => {
  const content = [{ id: "product-1" }, { id: "product-2" }];
  assert.deepEqual(toPage(content), {
    content, totalElements: 2, totalPages: 1, number: 0, size: 2, first: true, last: true,
  });
});

test("content-only responses keep their single-page fallback", () => {
  const content = [{ id: "product-1" }];
  assert.deepEqual(toPage({ content, page: null }), {
    content, totalElements: 1, totalPages: 1, number: 0, size: 1, first: true, last: true,
  });
});

test("invalid responses produce an empty page", () => {
  for (const value of [null, undefined, {}, { content: "invalid" }]) {
    assert.deepEqual(toPage(value), {
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true,
    });
  }
});
