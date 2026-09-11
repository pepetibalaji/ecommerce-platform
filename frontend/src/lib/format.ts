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
    const page = value as Partial<Page<T>> & {
      page?: Partial<Pick<Page<T>, "totalElements" | "totalPages" | "number" | "size">> | null;
    };
    // Spring Data's VIA_DTO response nests paging metadata under `page`.
    // Older services and mock responses still return these fields at the top level.
    const metadata = typeof page.page === "object" && page.page !== null ? page.page : undefined;
    const totalPages = metadata?.totalPages ?? page.totalPages ?? 1;
    const number = metadata?.number ?? page.number ?? 0;
    return {
      content: page.content ?? [],
      totalElements: metadata?.totalElements ?? page.totalElements ?? page.content?.length ?? 0,
      totalPages,
      number,
      size: metadata?.size ?? page.size ?? page.content?.length ?? 10,
      first: page.first ?? number === 0,
      last: page.last ?? number >= totalPages - 1,
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
  if (error instanceof Error) {
    const fields = "fields" in error && error.fields && typeof error.fields === "object" && !Array.isArray(error.fields)
      ? Object.entries(error.fields).filter(([, message]) => typeof message === "string" && message.trim()) : [];
    return fields.length > 0
      ? `${error.message} ${fields.map(([field, message]) => `${field}: ${message}`).join("; ")}` : error.message;
  }
  return "Something went wrong. Please try again.";
}
