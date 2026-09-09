import type { Cart, Inventory, NotificationRecord, Order, Page, Payment, Product, User } from "../domain";

const now = "2026-09-08T10:30:00";

export const mockProducts: Product[] = [
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd01", sellerId: "seller-001", name: "Linen Everyday Shirt", description: "A breathable, easy-fit linen shirt for workdays and weekends.", price: 1899, currency: "INR", category: "Apparel", brand: "Northline", imageUrls: [], active: true, createdAt: now, updatedAt: now },
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd02", sellerId: "seller-001", name: "Studio Desk Lamp", description: "Adjustable task lighting with a warm matte finish.", price: 2499, currency: "INR", category: "Home", brand: "Luma", imageUrls: [], active: true, createdAt: now, updatedAt: now },
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd03", sellerId: "seller-002", name: "Everyday Carry Tote", description: "Structured recycled-canvas tote with an internal pocket.", price: 1499, currency: "INR", category: "Accessories", brand: "Fieldwork", imageUrls: [], active: true, createdAt: now, updatedAt: now },
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd04", sellerId: "seller-001", name: "Ceramic Coffee Set", description: "Four hand-finished cups for unhurried mornings.", price: 3299, currency: "INR", category: "Home", brand: "Kora", imageUrls: [], active: true, createdAt: now, updatedAt: now },
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd05", sellerId: "seller-002", name: "Travel Notebook", description: "Dot-grid notebook with a resilient soft cover.", price: 599, currency: "INR", category: "Stationery", brand: "Papertrail", imageUrls: [], active: true, createdAt: now, updatedAt: now },
  { id: "0f17887c-468b-43ce-a01a-8e0941f9dd06", sellerId: "seller-001", name: "Weekend Headphones", description: "Comfort-first wireless headphones for focused listening.", price: 4999, currency: "INR", category: "Electronics", brand: "Form", imageUrls: [], active: false, createdAt: now, updatedAt: now },
];

export const mockCustomer: User = { id: "customer-001", name: "Asha Rao", email: "asha@example.com", role: "CUSTOMER", roles: ["CUSTOMER"], status: "ACTIVE" };
export const mockSeller: User = { id: "seller-001", name: "Nila Menon", email: "seller@example.com", role: "SELLER", roles: ["SELLER"], status: "ACTIVE" };
export const mockAdmin: User = { id: "admin-001", name: "Platform Admin", email: "admin@example.com", role: "ADMIN", roles: ["ADMIN"], permissions: ["USER:READ", "USER:STATUS_WRITE", "USER:ROLE_WRITE", "USER:SESSION_REVOKE"], status: "ACTIVE" };

export const mockUsers: User[] = [mockCustomer, mockSeller, mockAdmin, { id: "customer-002", name: "Dev Kumar", email: "dev@example.com", role: "CUSTOMER", roles: ["CUSTOMER"], status: "SUSPENDED" }];

export let mockCart: Cart = {
  userId: "guest-session",
  updatedAt: now,
  items: [
    { itemId: "cart-line-001", productId: mockProducts[0].id, quantity: 1 },
    { itemId: "cart-line-002", productId: mockProducts[1].id, quantity: 1 },
  ],
};

export const mockOrders: Order[] = [
  { id: "e0a10000-0000-4000-8000-000000000001", userId: "customer-001", totalAmount: 4398, currency: "INR", status: "PENDING", createdAt: now, updatedAt: now, cancelAllowed: true, shippingAddress: { recipientName: "Asha Rao", phone: "+919999999999", line1: "10 Market Road", city: "Bengaluru", state: "Karnataka", postalCode: "560001", country: "IN" }, items: [{ id: "order-line-1", productId: mockProducts[0].id, productName: mockProducts[0].name, quantity: 1, price: 1899, lineTotal: 1899 }, { id: "order-line-2", productId: mockProducts[1].id, productName: mockProducts[1].name, quantity: 1, price: 2499, lineTotal: 2499 }] },
  { id: "e0a10000-0000-4000-8000-000000000002", userId: "customer-001", totalAmount: 1499, currency: "INR", status: "CONFIRMED", createdAt: "2026-09-01T10:30:00", updatedAt: now, cancelAllowed: true, shippingAddress: { recipientName: "Asha Rao", phone: "+919999999999", line1: "10 Market Road", city: "Bengaluru", state: "Karnataka", postalCode: "560001", country: "IN" }, items: [{ id: "order-line-3", productId: mockProducts[2].id, productName: mockProducts[2].name, quantity: 1, price: 1499, lineTotal: 1499 }] },
];

export const mockPayments: Payment[] = [
  { id: "payment-001", orderId: mockOrders[0].id, amount: 4398, currency: "INR", provider: "Stripe", status: "REQUIRES_CUSTOMER_ACTION", expiresAt: "2026-09-08T11:00:00", createdAt: now },
  { id: "payment-002", orderId: mockOrders[1].id, amount: 1499, currency: "INR", provider: "Stripe", status: "SUCCESS", createdAt: "2026-09-01T10:30:00" },
];

export const mockInventory: Inventory[] = mockProducts.map((product, index) => ({ productId: product.id, availableStock: 20 + index * 4, reservedStock: index % 3 }));

export const mockNotifications: NotificationRecord[] = [
  { id: "notification-001", status: "FAILED", type: "PAYMENT_SUCCESSFUL", recipientId: "customer-001", message: "Delivery requires review", createdAt: now },
  { id: "notification-002", status: "FAILED", type: "ORDER_CANCELLED", recipientId: "customer-002", message: "Delivery requires review", createdAt: "2026-09-07T08:00:00" },
];

export function pageOf<T>(items: T[], page = 0, size = 10): Page<T> {
  const start = page * size;
  return { content: items.slice(start, start + size), totalElements: items.length, totalPages: Math.max(1, Math.ceil(items.length / size)), number: page, size, first: page === 0, last: start + size >= items.length };
}
