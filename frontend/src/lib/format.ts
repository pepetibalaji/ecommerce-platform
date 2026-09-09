import type { Page } from "../domain";

export function formatMoney(value: number, currency = "INR") {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(value);
}

export function formatDate(value?: string | null) {
  if (!value) return "—";
  // Current services may still return local ISO timestamps without an offset.
  // Do not silently relabel those values as UTC; offset-aware timestamps continue
  // to render correctly once the backend hardening contract is deployed.
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function toPage<T>(value: unknown): Page<T> {
  if (typeof value === "object" && value !== null && "content" in value && Array.isArray((value as { content: unknown }).content)) {
    const page = value as Partial<Page<T>>;
    return {
      content: page.content ?? [],
      totalElements: page.totalElements ?? page.content?.length ?? 0,
      totalPages: page.totalPages ?? 1,
      number: page.number ?? 0,
      size: page.size ?? page.content?.length ?? 10,
      first: page.first,
      last: page.last,
    };
  }
  if (Array.isArray(value)) {
    return { content: value as T[], totalElements: value.length, totalPages: 1, number: 0, size: value.length, first: true, last: true };
  }
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true };
}

export function createIdempotencyKey() {
  return crypto.randomUUID();
}

export function messageForError(error: unknown) {
  if (error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}
