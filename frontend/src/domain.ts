export type Role = "CUSTOMER" | "SELLER" | "ADMIN";

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DELETED";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  roles?: Role[];
  permissions?: string[];
  status: UserStatus;
}

export interface AuthSession {
  accessToken: string;
  tokenType?: string;
  expiresInSeconds?: number;
  user: User;
}

/**
 * A safe, self-service view of a browser session. The refresh credential is
 * intentionally never present here: it is held only in an HttpOnly cookie.
 */
export interface BrowserSession {
  id: string;
  createdAt?: string;
  lastUsedAt?: string;
  expiresAt?: string;
  deviceName?: string;
  ipAddress?: string;
  userAgent?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first?: boolean;
  last?: boolean;
}

export interface Product {
  id: string;
  sellerId?: string;
  name: string;
  description?: string | null;
  price: number;
  currency?: string;
  category?: string | null;
  brand?: string | null;
  imageUrls?: string[];
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CatalogueFacets {
  categories: Array<{ name: string; count: number }>;
  brands: Array<{ name: string; count: number }>;
  priceRange: { min: number | null; max: number | null };
}

export interface CartItem {
  itemId: string;
  productId: string;
  quantity: number;
}

export interface Cart {
  ownerType: "CUSTOMER" | "GUEST";
  ownerId: string;
  items: CartItem[];
  version: number;
  updatedAt: string;
}

export interface ShippingAddress {
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CANCELLATION_REQUESTED"
  | "REFUND_REQUESTED"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED"
  | "REFUND_FAILED"
  | "REFUND_REQUIRES_FULFILMENT_REVIEW"
  | "PAYMENT_FAILED"
  | "PAYMENT_EXPIRED"
  | "CANCELLED";

export interface OrderItem {
  id?: string;
  productId: string;
  productName?: string;
  quantity: number;
  /** Server-calculated snapshot price at the time the order was created. */
  unitPrice: number;
  lineTotal?: number;
}

export interface Order {
  id: string;
  userId?: string;
  sellerId?: string;
  totalAmount: number;
  currency: string;
  status: OrderStatus;
  paymentId?: string | null;
  paymentConfirmedAt?: string | null;
  paymentFailedAt?: string | null;
  paymentFailureReason?: string | null;
  createdAt?: string;
  updatedAt?: string;
  shippingAddress?: ShippingAddress;
  items: OrderItem[];
  cancelAllowed?: boolean;
  /** Explains why cancellation was accepted, rejected, or requires review. */
  cancellationReasonCode?: string | null;
}

/**
 * Seller-scoped order view. It deliberately excludes customer-wide totals and
 * payment state; the line currency is supplied for fulfilment totals.
 */
export interface SellerOrder {
  id: string;
  status: OrderStatus;
  createdAt?: string;
  shippingAddress?: ShippingAddress;
  currency: string;
  sellerTotalAmount: number;
  items: OrderItem[];
}

export interface OrderLifecycleAudit {
  id: string;
  action: string;
  actorId?: string | null;
  actorType?: string | null;
  reason?: string | null;
  refundRequestId?: string | null;
  createdAt: string;
}

export interface OutboxStatusCounts {
  pending: number;
  published: number;
  completed: number;
  failed: number;
  manualReview: number;
}

export interface OrderOutboxReconciliation {
  observedAt: string;
  orderCreated: OutboxStatusCounts;
  inventoryRelease: OutboxStatusCounts;
  checkoutCompensation: OutboxStatusCounts;
  refundRequest: OutboxStatusCounts;
}

export type PaymentStatus =
  | "PENDING"
  | "REQUIRES_CUSTOMER_ACTION"
  | "PROCESSING"
  | "SUCCESS"
  | "FAILED"
  | "EXPIRED"
  | "PARTIALLY_REFUNDED"
  | "CANCELLED"
  | "CANCELLATION_REQUESTED"
  | "REFUND_REQUESTED"
  | "REFUND_PROCESSING"
  | "REFUNDED"
  | "REFUND_FAILED";

export interface Payment {
  id: string;
  orderId: string;
  amount?: number;
  currency?: string;
  provider?: string;
  status: PaymentStatus;
  expiresAt?: string;
  checkoutUrl?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RefundResult {
  paymentId: string;
  refundId?: string;
  status: string;
}

export interface Inventory {
  productId: string;
  availableStock: number;
  reservedStock: number;
}

export interface NotificationRecord {
  id: string;
  status?: string;
  type?: string;
  createdAt?: string;
  recipientId?: string;
  message?: string;
}

export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly retryAfter?: number;
  readonly fields?: FieldErrors;
  /** Service-provided structured details (for example, checkout line failures). */
  readonly details?: unknown;
  readonly traceId?: string;
  readonly retryable: boolean;

  constructor(message: string, status: number, options?: {
    code?: string;
    retryAfter?: number;
    fields?: FieldErrors;
    retryable?: boolean;
    details?: unknown;
    traceId?: string;
  }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = options?.code;
    this.retryAfter = options?.retryAfter;
    this.fields = options?.fields;
    this.details = options?.details;
    this.traceId = options?.traceId;
    // A structured service value takes precedence; notably, a 409 does not
    // necessarily mean that retrying is safe (for example, key reuse).
    this.retryable = options?.retryable ?? (status === 0 || status === 429 || status >= 500);
  }
}
