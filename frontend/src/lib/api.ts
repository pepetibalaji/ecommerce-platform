import { ApiError, type AuthSession, type BrowserSession, type Cart, type CatalogueFacets, type Inventory, type NotificationRecord, type Order, type OrderLifecycleAudit, type OrderOutboxReconciliation, type Page, type Payment, type Product, type RefundResult, type Role, type SellerOrder, type ShippingAddress, type User } from "../domain";
import { mockAdmin, mockCart, mockCustomer, mockInventory, mockNotifications, mockOrders, mockPayments, mockProducts, mockSeller, mockUsers, pageOf } from "./mock-data";

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || "http://localhost:8080";
const API_BASE_URL = configuredBaseUrl.replace(/\/$/, "");
export const useMocks = import.meta.env.VITE_USE_MOCKS === "true";

type RequestOptions = Omit<RequestInit, "body" | "headers"> & {
  body?: unknown;
  token?: string | null;
  idempotencyKey?: string;
  headers?: Record<string, string>;
  skipSessionRecovery?: boolean;
};

type SessionRecoveryHandler = () => Promise<string | null>;
let sessionRecoveryHandler: SessionRecoveryHandler | null = null;

const mockOrderAudits = new Map<string, OrderLifecycleAudit[]>([
  [mockOrders[1].id, [{
    id: "audit-00000000-0000-4000-8000-000000000001",
    action: "REFUND_REQUESTED",
    actorId: mockCustomer.id,
    actorType: "CUSTOMER",
    reason: "Customer cancellation request",
    refundRequestId: "refund-request-00000000-0000-4000-8000-000000000001",
    createdAt: "2026-09-02T09:00:00Z",
  }]],
]);

/** Registers the single session owner that may rotate a browser access token. */
export function configureSessionRecovery(handler: SessionRecoveryHandler | null) {
  sessionRecoveryHandler = handler;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

const errorEnvelopeProperties = [
  "code", "message", "retryable", "details", "traceId", "fieldErrors",
  "timestamp", "status", "error", "path", "type", "title", "detail", "instance",
  "errors", "violations",
];

function isErrorEnvelope(value: Record<string, unknown>) {
  return errorEnvelopeProperties.some((key) => key in value);
}

function structuredError(value: unknown): {
  code?: string;
  retryable?: boolean;
  retryAfterSeconds?: number;
  details?: unknown;
  traceId?: string;
} | undefined {
  if (!isRecord(value) || !isErrorEnvelope(value)) return undefined;
  return {
    code: typeof value.code === "string" ? value.code : undefined,
    retryable: typeof value.retryable === "boolean" ? value.retryable : undefined,
    retryAfterSeconds: typeof value.retryAfterSeconds === "number" && Number.isFinite(value.retryAfterSeconds) ? value.retryAfterSeconds : undefined,
    details: "details" in value ? value.details : undefined,
    traceId: typeof value.traceId === "string" ? value.traceId : undefined,
  };
}

function fieldErrors(value: unknown): Record<string, string> | undefined {
  if (!isRecord(value)) return undefined;
  const fields = "fieldErrors" in value ? value.fieldErrors : isErrorEnvelope(value) ? undefined : value;
  if (!fields || typeof fields !== "object" || Array.isArray(fields)) return undefined;
  const entries = Object.entries(fields).filter(([, message]) => typeof message === "string" && message.trim());
  return entries.length > 0 ? Object.fromEntries(entries) : undefined;
}

function safeMessage(value: unknown, fallback: string) {
  if (isRecord(value)) {
    if (typeof value.detail === "string" && value.detail.trim()) return value.detail;
    if (typeof value.title === "string" && value.title.trim()) return value.title;
    if (typeof value.message === "string" && value.message.trim()) return value.message;
  }
  return fallback;
}

/** Maps the current Payment Service's `paymentId` wire field to the app's stable `id`. */
function paymentFromWire(value: unknown): Payment {
  const raw = value as Partial<Payment> & { paymentId?: string };
  const id = raw.id ?? raw.paymentId;
  if (!id || !raw.orderId || !raw.status) throw new ApiError("The payment response was incomplete. Please refresh the order.", 502);
  return { ...raw, id, orderId: raw.orderId, status: raw.status } as Payment;
}

function numberFromWire(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

/**
 * `unitPrice` is the current order contract. Reading the former `price`
 * field here keeps a rolling frontend/backend deployment from rendering a
 * zero-price order while the old response is still in flight.
 */
function orderItemFromWire(value: unknown): Order["items"][number] {
  if (!isRecord(value)) throw new ApiError("The order response was incomplete. Please refresh the order.", 502, { retryable: false });
  const quantity = numberFromWire(value.quantity);
  const unitPrice = numberFromWire(value.unitPrice) ?? numberFromWire(value.price);
  if (typeof value.productId !== "string" || typeof quantity !== "number" || !Number.isInteger(quantity) || quantity < 1 || unitPrice === undefined) {
    throw new ApiError("The order response was incomplete. Please refresh the order.", 502, { retryable: false });
  }
  const lineTotal = numberFromWire(value.lineTotal);
  return {
    ...(typeof value.id === "string" ? { id: value.id } : {}),
    productId: value.productId,
    ...(typeof value.productName === "string" ? { productName: value.productName } : {}),
    quantity,
    unitPrice,
    ...(lineTotal === undefined ? {} : { lineTotal }),
  };
}

function orderFromWire(value: unknown): Order {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new ApiError("The order response was incomplete. Please refresh the order.", 502, { retryable: false });
  }
  return { ...value, items: value.items.map(orderItemFromWire) } as Order;
}

function sellerOrderFromWire(value: unknown): SellerOrder {
  if (!isRecord(value) || !Array.isArray(value.items) || typeof value.currency !== "string" || !value.currency) {
    throw new ApiError("The seller order response was incomplete. Please refresh the queue.", 502, { retryable: false });
  }
  return { ...value, items: value.items.map(orderItemFromWire) } as SellerOrder;
}

function pageFromWire<T>(value: unknown, itemFromWire: (item: unknown) => T): Page<T> {
  if (isRecord(value) && Array.isArray(value.content)) {
    return { ...value, content: value.content.map(itemFromWire) } as Page<T>;
  }
  if (Array.isArray(value)) {
    return { content: value.map(itemFromWire), totalElements: value.length, totalPages: 1, number: 0, size: value.length, first: true, last: true };
  }
  throw new ApiError("The paged response was incomplete. Please refresh and try again.", 502, { retryable: false });
}

function orderPageFromWire(value: unknown): Page<Order> {
  return pageFromWire(value, orderFromWire);
}

function sellerOrderPageFromWire(value: unknown): Page<SellerOrder> {
  return pageFromWire(value, sellerOrderFromWire);
}

function paymentPageFromWire(value: unknown): Page<Payment> {
  if (value && typeof value === "object" && "content" in value && Array.isArray((value as { content?: unknown }).content)) {
    const page = value as Omit<Page<unknown>, "content"> & { content: unknown[] };
    return { ...page, content: page.content.map(paymentFromWire) };
  }
  if (Array.isArray(value)) {
    return { content: value.map(paymentFromWire), totalElements: value.length, totalPages: 1, number: 0, size: value.length, first: true, last: true };
  }
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true };
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (useMocks) return mockRequest<T>(path, options);

  const { body, token, idempotencyKey, headers: suppliedHeaders, skipSessionRecovery, ...fetchOptions } = options;
  const headers = new Headers(suppliedHeaders);
  headers.set("Accept", "application/json");
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (idempotencyKey) headers.set("Idempotency-Key", idempotencyKey);

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...fetchOptions,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "include",
    });
  } catch {
    throw new ApiError("You appear to be offline or the service is unavailable.", 0);
  }

  const retryAfter = Number(response.headers.get("Retry-After") ?? "") || undefined;
  const text = response.status === 204 ? "" : await response.text();
  let parsed: unknown = undefined;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && token && !skipSessionRecovery && sessionRecoveryHandler) {
      const replacementToken = await sessionRecoveryHandler();
      if (replacementToken) return request<T>(path, { ...options, token: replacementToken, skipSessionRecovery: true });
    }
    const fallback = response.status === 401
      ? "Your session has ended. Please sign in again."
      : response.status === 403
        ? "You do not have access to this area."
        : response.status === 429
          ? "Too many attempts. Please wait before trying again."
          : response.status >= 500
            ? "This service is temporarily unavailable. Please try again."
            : "We could not complete that request.";
    const envelope = structuredError(parsed);
    throw new ApiError(safeMessage(parsed, fallback), response.status, {
      code: envelope?.code,
      retryAfter: envelope?.retryAfterSeconds ?? retryAfter,
      fields: fieldErrors(parsed),
      retryable: envelope?.retryable,
      details: envelope?.details,
      traceId: envelope?.traceId,
    });
  }

  return parsed as T;
}

export const api = {
  auth: {
    login: (email: string, password: string) => request<AuthSession>("/api/v1/auth/login", { method: "POST", body: { email, password } }),
    register: (name: string, email: string, password: string) => request<void>("/api/v1/auth/register", { method: "POST", body: { name, email, password } }),
    refresh: () => request<AuthSession>("/api/v1/auth/refresh", { method: "POST", skipSessionRecovery: true }),
    logout: (token: string | null) => request<void>("/api/v1/auth/logout", { method: "POST", token, skipSessionRecovery: true }),
    resendVerification: (email: string) => request<void>("/api/v1/auth/verification/resend", { method: "POST", body: { email } }),
    confirmVerification: (token: string) => request<void>("/api/v1/auth/verification/confirm", { method: "POST", body: { token } }),
    forgotPassword: (email: string) => request<void>("/api/v1/auth/password/forgot", { method: "POST", body: { email } }),
    resetPassword: (token: string, newPassword: string) => request<void>("/api/v1/auth/password/reset", { method: "POST", body: { token, newPassword } }),
    confirmEmailChange: (token: string) => request<void>("/api/v1/auth/email-change/confirm", { method: "POST", body: { token } }),
  },
  products: {
    list: (query = "") => request<Page<Product>>(`/api/v1/products${query ? `?${query}` : ""}`),
    facets: () => request<CatalogueFacets>("/api/v1/products/facets"),
    byId: (productId: string) => request<Product>(`/api/v1/products/${productId}`),
    sellerList: (token: string, query = "page=0&size=10") => request<Page<Product>>(`/api/v1/seller/products?${query}`, { token }),
    sellerById: (token: string, productId: string) => request<Product>(`/api/v1/seller/products/${encodeURIComponent(productId)}`, { token }),
    sellerCreate: (token: string, product: Omit<Product, "id" | "sellerId">) => request<Product>("/api/v1/seller/products", { method: "POST", token, body: product }),
    sellerBulkCreate: (token: string, products: Array<Omit<Product, "id" | "sellerId">>) => request<Product[]>("/api/v1/seller/products/bulk", { method: "POST", token, body: products }),
    sellerUpdate: (token: string, productId: string, product: Omit<Product, "id" | "sellerId">) => request<Product>(`/api/v1/seller/products/${productId}`, { method: "PUT", token, body: product }),
    sellerArchive: (token: string, productId: string) => request<void>(`/api/v1/seller/products/${productId}`, { method: "DELETE", token }),
    adminById: (token: string, productId: string) => request<Product>(`/api/v1/admin/products/${encodeURIComponent(productId)}`, { token }),
    adminCreate: (token: string, sellerId: string, product: Omit<Product, "id" | "sellerId">) => request<Product>(`/api/v1/admin/products?sellerId=${encodeURIComponent(sellerId)}`, { method: "POST", token, body: product }),
    adminUpdate: (token: string, productId: string, product: Omit<Product, "id" | "sellerId">) => request<Product>(`/api/v1/admin/products/${productId}`, { method: "PUT", token, body: product }),
    adminArchive: (token: string, productId: string) => request<void>(`/api/v1/admin/products/${productId}`, { method: "DELETE", token }),
    replayDeadLetters: (token: string) => request<void>("/api/v1/admin/products/outbox/replay-dead-letters", { method: "POST", token }),
    reconcile: (token: string, afterId?: string) => request<{ enqueued: number; nextAfterId: string | null }>(`/api/v1/admin/products/outbox/reconcile?size=100${afterId ? `&afterId=${encodeURIComponent(afterId)}` : ""}`, { method: "POST", token }),
  },
  cart: {
    guest: () => request<Cart>("/api/v1/cart/guest"),
    customer: (token: string) => request<Cart>("/api/v1/cart", { token }),
    addGuest: (productId: string, quantity: number, idempotencyKey: string) => request<Cart>("/api/v1/cart/guest/items", { method: "POST", body: { productId, quantity }, idempotencyKey }),
    addCustomer: (token: string, productId: string, quantity: number, idempotencyKey: string) => request<Cart>("/api/v1/cart", { method: "POST", token, body: { productId, quantity }, idempotencyKey }),
    updateGuest: (itemId: string, quantity: number) => request<Cart>(`/api/v1/cart/guest/items/${itemId}`, { method: "PUT", body: { quantity } }),
    updateCustomer: (token: string, itemId: string, quantity: number) => request<Cart>(`/api/v1/cart/${itemId}`, { method: "PUT", token, body: { quantity } }),
    removeGuest: (itemId: string) => request<Cart>(`/api/v1/cart/guest/items/${itemId}`, { method: "DELETE" }),
    removeCustomer: (token: string, itemId: string) => request<Cart>(`/api/v1/cart/${itemId}`, { method: "DELETE", token }),
    mergeGuest: (token: string, idempotencyKey: string) => request<Cart>("/api/v1/cart/merge-guest", { method: "POST", token, idempotencyKey }),
  },
  users: {
    me: (token: string) => request<User>("/api/v1/users/me", { token }),
    update: (token: string, name: string) => request<User>("/api/v1/users/me", { method: "PUT", token, body: { name } }),
    delete: (token: string) => request<void>("/api/v1/users/me", { method: "DELETE", token }),
    requestEmailChange: (token: string, email: string) => request<void>("/api/v1/users/me/email-change", { method: "POST", token, body: { email } }),
    changePassword: (token: string, currentPassword: string, newPassword: string) => request<void>("/api/v1/users/me/password", { method: "POST", token, body: { currentPassword, newPassword } }),
    sessions: (token: string) => request<BrowserSession[]>("/api/v1/users/me/sessions", { token }),
    revokeSession: (token: string, sessionId: string) => request<void>(`/api/v1/users/me/sessions/${sessionId}`, { method: "DELETE", token }),
  },
  orders: {
    list: (token: string, query = "page=0&size=10") => request<unknown>(`/api/v1/orders?${query}`, { token }).then(orderPageFromWire),
    byId: (token: string, orderId: string) => request<unknown>(`/api/v1/orders/${encodeURIComponent(orderId)}`, { token }).then(orderFromWire),
    create: (token: string, items: Array<{ productId: string; quantity: number }>, shippingAddress: ShippingAddress, currency: string, idempotencyKey: string) => request<unknown>("/api/v1/orders", { method: "POST", token, idempotencyKey, body: { items, shippingAddress, currency } }).then(orderFromWire),
    cancel: (token: string, orderId: string) => request<unknown>(`/api/v1/orders/${encodeURIComponent(orderId)}/cancel`, { method: "PUT", token }).then(orderFromWire),
    sellerList: (token: string, query = "page=0&size=10") => request<unknown>(`/api/v1/seller/orders?${query}`, { token }).then(sellerOrderPageFromWire),
    adminList: (token: string, query = "page=0&size=10") => request<unknown>(`/api/v1/admin/orders?${query}`, { token }).then(orderPageFromWire),
    /** Requests a refund; completion is asynchronous and is reflected in the order/audit response. */
    adminRequestRefund: (token: string, orderId: string, reason: string) => request<unknown>(`/api/v1/admin/orders/${encodeURIComponent(orderId)}/refund-requests`, { method: "POST", token, body: { reason } }).then(orderFromWire),
    adminAudit: (token: string, orderId: string) => request<OrderLifecycleAudit[]>(`/api/v1/admin/orders/${encodeURIComponent(orderId)}/audit`, { token }),
    adminOutboxReconciliation: (token: string) => request<OrderOutboxReconciliation>("/api/v1/admin/orders/reconciliation/outboxes", { token }),
  },
  payments: {
    refresh: (token: string, orderId: string) => request<unknown>(`/api/v1/payments/orders/${encodeURIComponent(orderId)}`, { token }).then(paymentFromWire),
    byOrder: (token: string, orderId: string) => request<unknown>(`/api/v1/payments/orders/${orderId}`, { token }).then(paymentFromWire),
    checkoutSession: (token: string, orderId: string) => request<unknown>(`/api/v1/payments/orders/${orderId}/checkout-session`, { method: "POST", token }).then(paymentFromWire),
    mine: (token: string, query = "page=0&size=10") => request<unknown>(`/api/v1/payments/me?${query}`, { token }).then(paymentPageFromWire),
    adminList: (token: string, query = "page=0&size=10") => request<unknown>(`/api/v1/admin/payments?${query}`, { token }).then(paymentPageFromWire),
    adminById: (token: string, paymentId: string) => request<unknown>(`/api/v1/admin/payments/${paymentId}`, { token }).then(paymentFromWire),
    refund: (token: string, paymentId: string, body: { orderId: string; amount: number; currency: string; reason?: string }, idempotencyKey: string) => request<RefundResult>(`/api/v1/admin/payments/${paymentId}/refund`, { method: "POST", token, body: { ...body, idempotencyKey }, idempotencyKey }),
  },
  inventory: {
    sellerCreate: (token: string, productId: string, availableStock: number) => request<Inventory>("/api/v1/seller/inventory", { method: "POST", token, body: { productId, availableStock } }),
    sellerGet: (token: string, productId: string) => request<Inventory>(`/api/v1/seller/inventory/${productId}`, { token }),
    sellerAdjust: (token: string, productId: string, body: { adjustment: number; reason: string; referenceId?: string }) => request<Inventory>(`/api/v1/seller/inventory/${productId}/adjustments`, { method: "POST", token, body }),
    adminCreate: (token: string, productId: string, availableStock: number) => request<Inventory>("/api/v1/admin/inventory", { method: "POST", token, body: { productId, availableStock } }),
    adminGet: (token: string, productId: string) => request<Inventory>(`/api/v1/admin/inventory/${productId}`, { token }),
    adminAdjust: (token: string, productId: string, body: { adjustment: number; reason: string; referenceId?: string }) => request<Inventory>(`/api/v1/admin/inventory/${productId}/adjustments`, { method: "POST", token, body }),
    adminOperations: (token: string) => request<{ pendingOutboxEvents: number; deadOutboxEvents: number; generatedAt: string }>("/api/v1/admin/inventory/operations", { token }),
  },
  admin: {
    users: (token: string, query = "page=0&size=10") => request<Page<User>>(`/api/v1/admin/users?${query}`, { token }),
    user: (token: string, userId: string) => request<User>(`/api/v1/admin/users/${userId}`, { token }),
    userStatus: (token: string, userId: string, status: User["status"]) => request<void>(`/api/v1/admin/users/${userId}/status`, { method: "PATCH", token, body: { status } }),
    userRoles: (token: string, userId: string, roles: Role[]) => request<User>(`/api/v1/admin/users/${userId}/roles`, { method: "PUT", token, body: { roles } }),
    deleteUser: (token: string, userId: string) => request<void>(`/api/v1/admin/users/${userId}`, { method: "DELETE", token }),
    revokeUserSessions: (token: string, userId: string) => request<void>(`/api/v1/admin/users/${userId}/sessions`, { method: "DELETE", token }),
    replayAuthOutboxDeadLetters: (token: string) => request<{ replayed: number }>("/api/v1/admin/outbox/replay-dead-letters", { method: "POST", token }),
    failedNotifications: (token: string) => request<NotificationRecord[]>("/api/v1/notifications/admin/failed", { token }),
    notificationDeliveries: (token: string, notificationId: string) => request<NotificationRecord[]>(`/api/v1/notifications/admin/${notificationId}/deliveries`, { token }),
  },
};

function resolveMockUser(email: string): User {
  if (email.toLowerCase().includes("admin")) return mockAdmin;
  if (email.toLowerCase().includes("seller")) return mockSeller;
  return mockCustomer;
}

let mockBrowserUser: User | null = null;
let mockBrowserSessions: BrowserSession[] = [];

function resolveMockUserFromToken(token?: string | null): User {
  return resolveMockUser(token ?? "");
}

async function mockRequest<T>(path: string, options: RequestOptions): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, 180));
  const [pathname, rawQuery = ""] = path.split("?");
  const params = new URLSearchParams(rawQuery);
  const method = options.method ?? "GET";
  const body = (options.body ?? {}) as Record<string, unknown>;
  const page = Number(params.get("page") ?? 0);
  const size = Number(params.get("size") ?? 10);

  if (pathname === "/api/v1/auth/login" && method === "POST") {
    const user = resolveMockUser(String(body.email ?? ""));
    mockBrowserUser = user;
    mockBrowserSessions = [{ id: "session-current", createdAt: nowIso(), lastUsedAt: nowIso(), expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), deviceName: "This browser", ipAddress: "127.0.0.1", userAgent: "Pepekart demo browser" }];
    return { accessToken: `mock-${user.role.toLowerCase()}-token`, tokenType: "Bearer", expiresInSeconds: 1800, user } as T;
  }
  if (pathname === "/api/v1/auth/refresh" && method === "POST") {
    if (!mockBrowserUser) throw new ApiError("Your session has ended. Please sign in again.", 401);
    return { accessToken: `mock-${mockBrowserUser.role.toLowerCase()}-token`, tokenType: "Bearer", expiresInSeconds: 1800, user: mockBrowserUser } as T;
  }
  if (pathname === "/api/v1/auth/logout" && method === "POST") { mockBrowserUser = null; mockBrowserSessions = []; return undefined as T; }
  if (pathname.startsWith("/api/v1/auth/")) return undefined as T;
  if (pathname === "/api/v1/users/me" && method === "GET") return resolveMockUserFromToken(options.token) as T;
  if (pathname === "/api/v1/users/me" && method === "PUT") {
    const user = resolveMockUserFromToken(options.token);
    return { ...user, name: String(body.name ?? user.name) } as T;
  }
  if (pathname === "/api/v1/users/me/sessions" && method === "GET") return mockBrowserSessions as T;
  if (pathname.startsWith("/api/v1/users/me/sessions/") && method === "DELETE") {
    const sessionId = pathname.split("/").at(-1);
    mockBrowserSessions = mockBrowserSessions.filter((session) => session.id !== sessionId);
    return undefined as T;
  }
  if (pathname === "/api/v1/admin/outbox/replay-dead-letters" && method === "POST") return { replayed: 0 } as T;
  if (pathname === "/api/v1/products") {
    const term = params.get("q")?.toLowerCase();
    const category = params.get("category");
    const brand = params.get("brand"); const minPrice = Number(params.get("minPrice")?.trim() || Number.NaN); const maxPrice = Number(params.get("maxPrice")?.trim() || Number.NaN); const sort = params.get("sort") ?? "newest";
    const visible = mockProducts.filter((product) => product.active !== false).filter((product) => !term || `${product.name} ${product.brand} ${product.description}`.toLowerCase().includes(term)).filter((product) => !category || product.category === category).filter((product) => !brand || product.brand === brand).filter((product) => !Number.isFinite(minPrice) || product.price >= minPrice).filter((product) => !Number.isFinite(maxPrice) || product.price <= maxPrice).sort((a, b) => sort === "price_asc" ? a.price - b.price : sort === "price_desc" ? b.price - a.price : sort === "name_asc" ? a.name.localeCompare(b.name) : sort === "name_desc" ? b.name.localeCompare(a.name) : 0);
    return pageOf(visible, page, size) as T;
  }
  if (pathname === "/api/v1/products/facets") {
    const visible = mockProducts.filter((product) => product.active !== false);
    const grouped = (key: "category" | "brand") => [...new Map(visible.filter((product) => product[key]).map((product) => [product[key]!, visible.filter((item) => item[key] === product[key]).length])).entries()].map(([name, count]) => ({ name, count }));
    return { categories: grouped("category"), brands: grouped("brand"), priceRange: { min: Math.min(...visible.map((product) => product.price)), max: Math.max(...visible.map((product) => product.price)) } } as T;
  }
  if (pathname.startsWith("/api/v1/products/")) {
    const found = mockProducts.find((product) => product.id === pathname.split("/").at(-1));
    if (!found || found.active === false) throw new ApiError("This product is no longer available.", 404);
    return found as T;
  }
  if ((pathname === "/api/v1/cart/guest" || pathname === "/api/v1/cart") && method === "GET") return mockCart as T;
  if (pathname === "/api/v1/cart/guest/items" || pathname === "/api/v1/cart") {
    const productId = String(body.productId);
    const quantity = Number(body.quantity);
    const existing = mockCart.items.find((item) => item.productId === productId);
    if (existing) existing.quantity += quantity;
    else mockCart.items.push({ itemId: `cart-line-${Date.now()}`, productId, quantity });
    return mockCart as T;
  }
  if (pathname.includes("/cart/") && method === "PUT") {
    const item = mockCart.items.find((entry) => entry.itemId === pathname.split("/").at(-1));
    if (!item) throw new ApiError("That cart item is no longer available.", 404);
    item.quantity = Number(body.quantity);
    return mockCart as T;
  }
  if (pathname.includes("/cart/") && method === "DELETE") {
    const itemId = pathname.split("/").at(-1);
    mockCart.items = mockCart.items.filter((item) => item.itemId !== itemId);
    return mockCart as T;
  }
  if (pathname === "/api/v1/cart/merge-guest") return { ...mockCart, ownerType: "CUSTOMER", ownerId: mockCustomer.id } as T;
  if (pathname === "/api/v1/orders" && method === "GET") return pageOf(mockOrders, page, size) as T;
  if (pathname === "/api/v1/orders" && method === "POST") {
    const items = (body.items ?? []) as Array<{ productId: string; quantity: number }>;
    const next: Order = { id: crypto.randomUUID(), userId: mockCustomer.id, totalAmount: items.reduce((total, line) => total + (mockProducts.find((product) => product.id === line.productId)?.price ?? 0) * line.quantity, 0), currency: String(body.currency ?? "INR"), status: "PENDING", createdAt: nowIso(), updatedAt: nowIso(), cancelAllowed: true, shippingAddress: body.shippingAddress as Order["shippingAddress"], items: items.map((line) => { const product = mockProducts.find((entry) => entry.id === line.productId)!; return { productId: line.productId, productName: product.name, quantity: line.quantity, unitPrice: product.price, lineTotal: product.price * line.quantity }; }) };
    mockOrders.unshift(next);
    mockPayments.unshift({ id: crypto.randomUUID(), orderId: next.id, amount: next.totalAmount, currency: next.currency, provider: "Mock checkout", status: "REQUIRES_CUSTOMER_ACTION", createdAt: nowIso(), updatedAt: nowIso() });
    return next as T;
  }
  if (pathname.startsWith("/api/v1/orders/") && pathname.endsWith("/cancel")) {
    const order = mockOrders.find((entry) => entry.id === pathname.split("/")[4]);
    if (!order) throw new ApiError("Order not found.", 404);
    if (order.status === "PENDING") {
      order.status = "CANCELLED";
      order.cancellationReasonCode = "ORDER_CANCELLATION_NOT_ALLOWED";
    } else if (order.status === "CONFIRMED") {
      order.status = "REFUND_REQUESTED";
      order.cancellationReasonCode = "REFUND_IN_PROGRESS";
      const payment = mockPayments.find((entry) => entry.orderId === order.id);
      if (payment) payment.status = "REFUND_PROCESSING";
      const audit: OrderLifecycleAudit = {
        id: crypto.randomUUID(),
        action: "REFUND_REQUESTED",
        actorId: mockCustomer.id,
        actorType: "CUSTOMER",
        reason: null,
        refundRequestId: crypto.randomUUID(),
        createdAt: nowIso(),
      };
      mockOrderAudits.set(order.id, [...(mockOrderAudits.get(order.id) ?? []), audit]);
    } else if (order.status !== "REFUND_REQUESTED") {
      throw new ApiError("This order cannot be cancelled in its current lifecycle state.", 409, { code: "ORDER_CANCELLATION_NOT_ALLOWED", retryable: false });
    }
    order.cancelAllowed = false;
    order.updatedAt = nowIso();
    return order as T;
  }
  if (pathname.startsWith("/api/v1/orders/")) {
    const order = mockOrders.find((entry) => entry.id === pathname.split("/").at(-1));
    if (!order) throw new ApiError("Order not found.", 404);
    return order as T;
  }
  if (pathname.startsWith("/api/v1/payments/orders/") && pathname.endsWith("/checkout-session")) {
    const orderId = pathname.split("/")[5];
    const payment = mockPayments.find((entry) => entry.orderId === orderId);
    if (!payment) throw new ApiError("Payment is still being prepared. Please refresh shortly.", 404, { code: "PAYMENT_PREPARING", retryable: true, retryAfter: 2 });
    const origin = typeof window === "undefined" ? "http://localhost:5173" : window.location.origin;
    // A mock browser redirect, like a real redirect, carries no proof of payment.
    payment.status = "REQUIRES_CUSTOMER_ACTION";
    payment.expiresAt = new Date(Date.now() + 30 * 60 * 1000).toISOString();
    return { ...payment, checkoutUrl: `${origin}/payment/return?orderId=${encodeURIComponent(orderId)}&paymentId=${encodeURIComponent(payment.id)}` } as T;
  }
  if (pathname.startsWith("/api/v1/payments/orders/")) {
    const payment = mockPayments.find((entry) => entry.orderId === pathname.split("/")[5]);
    if (!payment) throw new ApiError("Payment is still being prepared. Please refresh shortly.", 404, { code: "PAYMENT_PREPARING", retryable: true, retryAfter: 2 });
    return payment as T;
  }
  if (pathname === "/api/v1/payments/me") return pageOf(mockPayments, page, size) as T;
  if (pathname === "/api/v1/seller/products") {
    if (method === "GET") return pageOf(mockProducts.filter((product) => product.sellerId === mockSeller.id), page, size) as T;
    const created: Product = { id: crypto.randomUUID(), sellerId: mockSeller.id, active: true, name: String(body.name), price: Number(body.price), description: String(body.description ?? ""), category: String(body.category ?? ""), brand: String(body.brand ?? ""), imageUrls: (body.imageUrls ?? []) as string[], currency: "INR", createdAt: nowIso(), updatedAt: nowIso() };
    mockProducts.push(created);
    return created as T;
  }
  if (pathname.startsWith("/api/v1/seller/products/") && pathname !== "/api/v1/seller/products/bulk") {
    const productId = pathname.split("/").at(-1)!;
    const product = mockProducts.find((entry) => entry.id === productId && entry.sellerId === mockSeller.id);
    if (!product) throw new ApiError("Product not found.", 404);
    if (method === "DELETE") { product.active = false; product.updatedAt = nowIso(); return undefined as T; }
    if (method === "PUT") return Object.assign(product, body, { price: Number(body.price), updatedAt: nowIso() }) as T;
    return product as T;
  }
  if (pathname === "/api/v1/seller/orders") {
    const sellerOrders: SellerOrder[] = mockOrders
      .map((order) => {
        const items = order.items.filter((item) => mockProducts.find((product) => product.id === item.productId)?.sellerId === mockSeller.id);
        return {
          id: order.id,
          status: order.status,
          createdAt: order.createdAt,
          shippingAddress: order.shippingAddress,
          currency: order.currency,
          sellerTotalAmount: items.reduce((total, item) => total + (item.lineTotal ?? item.unitPrice * item.quantity), 0),
          items,
        };
      })
      .filter((order) => order.items.length > 0);
    return pageOf(sellerOrders, page, size) as T;
  }
  if (pathname === "/api/v1/seller/inventory" && method === "POST") {
    const productId = String(body.productId);
    if (mockInventory.some((entry) => entry.productId === productId)) throw new ApiError("Inventory already exists for this product.", 409);
    const inventory: Inventory = { productId, availableStock: Number(body.availableStock), reservedStock: 0 };
    mockInventory.push(inventory);
    return inventory as T;
  }
  if (pathname.startsWith("/api/v1/seller/inventory/")) {
    const segments = pathname.split("/");
    const productId = segments[5]!;
    const inventory = mockInventory.find((entry) => entry.productId === productId);
    if (!inventory) throw new ApiError("Inventory not found.", 404);
    if (method === "POST" && pathname.endsWith("/adjustments")) {
      const adjustment = Number(body.adjustment);
      if (!Number.isInteger(adjustment) || adjustment === 0) throw new ApiError("Adjustment must be a non-zero whole number.", 400);
      if (inventory.availableStock + adjustment < 0) throw new ApiError("Adjustment would make available stock negative.", 409);
      inventory.availableStock += adjustment;
    }
    return inventory as T;
  }
  if (pathname === "/api/v1/admin/users") return pageOf(mockUsers, page, size) as T;
  if (pathname.startsWith("/api/v1/admin/users/")) {
    const parts = pathname.split("/");
    const target = mockUsers.find((user) => user.id === parts[5]);
    if (!target) throw new ApiError("User not found.", 404);
    if (method === "DELETE" && parts.length === 6) { target.status = "DELETED"; return undefined as T; }
    if (pathname.endsWith("/status")) target.status = String(body.status) as User["status"];
    if (pathname.endsWith("/roles")) { target.roles = body.roles as Role[]; target.role = target.roles[0] ?? "CUSTOMER"; }
    return target as T;
  }
  if (pathname === "/api/v1/admin/orders") return pageOf(mockOrders, page, size) as T;
  if (pathname === "/api/v1/admin/orders/reconciliation/outboxes") {
    const emptyCounts = { pending: 0, published: 0, completed: 0, failed: 0, manualReview: 0 };
    return { observedAt: nowIso(), orderCreated: emptyCounts, inventoryRelease: emptyCounts, checkoutCompensation: emptyCounts, refundRequest: emptyCounts } as T;
  }
  if (pathname.startsWith("/api/v1/admin/orders/") && pathname.endsWith("/audit")) {
    const orderId = pathname.split("/")[5];
    return (mockOrderAudits.get(orderId) ?? []) as T;
  }
  if (pathname.startsWith("/api/v1/admin/orders/") && pathname.endsWith("/refund-requests") && method === "POST") {
    const order = mockOrders.find((entry) => entry.id === pathname.split("/")[5]);
    if (!order) throw new ApiError("Order not found.", 404);
    if (!String(body.reason ?? "").trim()) throw new ApiError("Refund reason is required.", 400, { retryable: false });
    if (order.status === "CONFIRMED") {
      order.status = "REFUND_REQUESTED";
      order.cancelAllowed = false;
      order.cancellationReasonCode = "REFUND_IN_PROGRESS";
      order.updatedAt = nowIso();
      const payment = mockPayments.find((entry) => entry.orderId === order.id);
      if (payment) payment.status = "REFUND_PROCESSING";
      const audit: OrderLifecycleAudit = {
        id: crypto.randomUUID(),
        action: "REFUND_REQUESTED",
        actorId: mockAdmin.id,
        actorType: "ADMIN",
        reason: String(body.reason).trim(),
        refundRequestId: crypto.randomUUID(),
        createdAt: nowIso(),
      };
      mockOrderAudits.set(order.id, [...(mockOrderAudits.get(order.id) ?? []), audit]);
      return order as T;
    }
    if (order.status === "REFUND_REQUESTED") return order as T;
    throw new ApiError("A refund can only be requested for a confirmed order.", 409, { code: "ORDER_STATE_CONFLICT", retryable: false });
  }
  if (pathname === "/api/v1/admin/payments") return pageOf(mockPayments, page, size) as T;
  if (pathname.startsWith("/api/v1/admin/payments/")) {
    const payment = mockPayments.find((entry) => entry.id === pathname.split("/")[5]);
    if (!payment) throw new ApiError("Payment not found.", 404);
    if (pathname.endsWith("/refund")) {
      payment.status = "REFUND_REQUESTED";
      return { paymentId: payment.id, refundId: crypto.randomUUID(), status: payment.status } as T;
    }
    return payment as T;
  }
  if (pathname === "/api/v1/admin/inventory" && method === "POST") {
    const productId = String(body.productId);
    if (mockInventory.some((entry) => entry.productId === productId)) throw new ApiError("Inventory already exists for this product.", 409);
    const inventory: Inventory = { productId, availableStock: Number(body.availableStock), reservedStock: 0 };
    mockInventory.push(inventory);
    return inventory as T;
  }
  if (pathname.startsWith("/api/v1/admin/inventory/")) {
    if (pathname === "/api/v1/admin/inventory/operations") return { pendingOutboxEvents: 0, deadOutboxEvents: 0, generatedAt: nowIso() } as T;
    const segments = pathname.split("/");
    const productId = segments[5]!;
    const inventory = mockInventory.find((entry) => entry.productId === productId);
    if (!inventory) throw new ApiError("Inventory not found.", 404);
    if (method === "POST" && pathname.endsWith("/adjustments")) {
      const adjustment = Number(body.adjustment);
      if (!Number.isInteger(adjustment) || adjustment === 0) throw new ApiError("Adjustment must be a non-zero whole number.", 400);
      if (inventory.availableStock + adjustment < 0) throw new ApiError("Adjustment would make available stock negative.", 409);
      inventory.availableStock += adjustment;
    }
    return inventory as T;
  }
  if (pathname === "/api/v1/admin/products" && method === "POST") {
    const created: Product = { id: crypto.randomUUID(), sellerId: params.get("sellerId") ?? undefined, active: true, name: String(body.name), price: Number(body.price), description: String(body.description ?? ""), category: String(body.category ?? ""), brand: String(body.brand ?? ""), imageUrls: (body.imageUrls ?? []) as string[], currency: "INR", createdAt: nowIso(), updatedAt: nowIso() };
    mockProducts.push(created);
    return created as T;
  }
  if (pathname === "/api/v1/seller/products/bulk" && method === "POST") {
    if (!Array.isArray(body)) throw new ApiError("Bulk product import requires an array.", 400);
    const created = body.map((item) => ({ id: crypto.randomUUID(), sellerId: mockSeller.id, active: true, name: String(item.name), price: Number(item.price), description: String(item.description ?? ""), category: String(item.category ?? ""), brand: String(item.brand ?? ""), imageUrls: (item.imageUrls ?? []) as string[], currency: String(item.currency ?? "USD"), createdAt: nowIso(), updatedAt: nowIso() }));
    mockProducts.push(...created); return created as T;
  }
  if (pathname === "/api/v1/admin/products/outbox/replay-dead-letters" && method === "POST") return undefined as T;
  if (pathname === "/api/v1/admin/products/outbox/reconcile" && method === "POST") {
    const afterId = params.get("afterId");
    const products = [...mockProducts].sort((a, b) => a.id.localeCompare(b.id)).filter(product => !afterId || product.id.localeCompare(afterId) > 0);
    const batch = products.slice(0, size);
    return { enqueued: batch.length, nextAfterId: products.length > size ? batch.at(-1)?.id ?? null : null } as T;
  }
  if (pathname.startsWith("/api/v1/admin/products/")) {
    const product = mockProducts.find((entry) => entry.id === pathname.split("/").at(-1));
    if (!product) throw new ApiError("Product not found.", 404);
    if (method === "DELETE") { product.active = false; product.updatedAt = nowIso(); return undefined as T; }
    if (method === "PUT") return Object.assign(product, body, { price: Number(body.price), updatedAt: nowIso() }) as T;
    return product as T;
  }
  if (pathname === "/api/v1/notifications/admin/failed") return mockNotifications as T;
  if (pathname.startsWith("/api/v1/notifications/admin/") && pathname.endsWith("/deliveries")) return mockNotifications as T;
  throw new ApiError(`Mock endpoint is not configured: ${method} ${pathname}`, 501);
}

function nowIso() {
  return new Date().toISOString().slice(0, 19);
}
