import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import postcss from "postcss";

const sheets = Object.fromEntries(["storefront-shell", "catalogue-experience", "product-detail", "backoffice-experience"].map(name => [name,
  postcss.parse(readFileSync(new URL(`../src/styles/${name}.css`, import.meta.url), "utf8")),
]));

// Check the final explicit declaration, including responsive rules. Browser layout
// checks are still required; these guard the known scrolling/overflow regressions.
function lastDeclaration(sheet, selector, property) {
  let value;
  sheets[sheet].walkRules(rule => {
    if (!rule.selectors.includes(selector)) return;
    rule.walkDecls(property, declaration => { value = declaration.value; });
  });
  return value;
}

test("storefront navigation and catalogue filters do not float over results", () => {
  assert.equal(lastDeclaration("storefront-shell", ".storefront-v2 .sf-header", "position"), "relative");
  assert.equal(lastDeclaration("storefront-shell", ".storefront-v2 .sf-header", "top"), "auto");
  assert.equal(lastDeclaration("catalogue-experience", ".storefront-v2 .shop-toolbar", "position"), "relative");
  assert.equal(lastDeclaration("catalogue-experience", ".storefront-v2 .shop-toolbar", "top"), "auto");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .bo-topbar", "position"), "static");
});

test("workspace tables keep horizontal overflow accessible on narrow screens", () => {
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .table-wrap", "overflow-x"), "auto");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .data-table", "min-width"), "700px");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .data-table .action-row", "flex-direction"), "row");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .data-table .action-row .button", "width"), "auto");
});

test("workspace loading feedback stays inline without an opaque pagination panel", () => {
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .infinite-list-footer", "background"), "transparent");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .infinite-list-footer", "box-shadow"), "none");
  assert.equal(lastDeclaration("backoffice-experience", ".bo-shell .infinite-list-sentinel", "height"), "1px");
});
