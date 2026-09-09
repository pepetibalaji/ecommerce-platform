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
  refreshToken?: string;
  tokenType?: string;
  expiresInSeconds?: number;
  user: User;
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

export interface CartItem {
  itemId: string;
  productId: string;
  quantity: number;
}

export interface Cart {
  userId?: string;
  items: CartItem[];
  version?: string | number;
  updatedAt?: string;
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
  | "PARTIALLY_REFUNDED"
  | "REFUNDED"
  | "REFUND_REQUIRES_FULFILMENT_REVIEW"
  | "PAYMENT_FAILED"
  | "CANCELLED";

export interface OrderItem {
  id?: string;
  productId: string;
  productName?: string;
  quantity: number;
  price: number;
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
  createdAt?: string;
  updatedAt?: string;
  shippingAddress?: ShippingAddress;
  items: OrderItem[];
  cancelAllowed?: boolean;
  sellerSubtotal?: number;
  sellerTotalAmount?: number;
}

export type PaymentStatus =
  | "PENDING"
  | "REQUIRES_CUSTOMER_ACTION"
  | "PROCESSING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELLED"
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
  readonly retryAfter?: number;
  readonly fields?: FieldErrors;
  readonly retryable: boolean;

  constructor(message: string, status: number, options?: { retryAfter?: number; fields?: FieldErrors }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfter = options?.retryAfter;
    this.fields = options?.fields;
    this.retryable = status === 0 || status === 429 || status === 503 || status === 504;
  }
}
