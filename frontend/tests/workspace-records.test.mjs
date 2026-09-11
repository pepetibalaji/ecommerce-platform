import assert from "node:assert/strict";
import test from "node:test";
import { filterWorkspaceRecords } from "../src/lib/workspace-records.ts";

const records = [
  { id: "1", name: "Canvas Bag", category: "Accessories", roles: ["SELLER"], note: null },
  { id: "2", name: "Desk lamp", category: "Home", roles: ["CUSTOMER", "ADMIN"] },
];
const fields = record => [record.id, record.name, record.category, record.roles, record.note];

test("blank page searches preserve records and ordering", () => {
  assert.equal(filterWorkspaceRecords(records, "  ", fields), records);
});
test("page search trims and matches case-insensitively across visible fields", () => {
  assert.deepEqual(filterWorkspaceRecords(records, "  CANVAS ", fields), [records[0]]);
  assert.deepEqual(filterWorkspaceRecords(records, "home", fields), [records[1]]);
  assert.deepEqual(filterWorkspaceRecords(records, "admin", fields), [records[1]]);
});
test("search does not invent records or treat absent values as searchable text", () => {
  assert.deepEqual(filterWorkspaceRecords(records, "null", fields), []);
  assert.deepEqual(filterWorkspaceRecords(records, "undefined", fields), []);
  assert.deepEqual(filterWorkspaceRecords([], "bag", fields), []);
  assert.equal(records.length, 2);
});
