import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { ArrowLeft, ArrowRight, ArrowUpRight, Check, CheckCircle2, ChevronRight, CircleAlert, Clock3, CreditCard, KeyRound, Laptop, LockKeyhole, Mail, MapPin, Package, ReceiptText, RefreshCw, ShieldCheck, ShoppingBag, SlidersHorizontal, Sparkles, Trash2, UserRound } from "lucide-react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import { ApiError, type BrowserSession, type Order, type Payment, type Product, type ShippingAddress } from "../domain";
import { api, useMocks } from "../lib/api";
import { createIdempotencyKey, formatDate, formatMoney, messageForError, toPage } from "../lib/format";
import { useResource } from "../lib/hooks";
import { useInfinitePage } from "../lib/useInfinitePage";
import { pollPaymentConfirmation, approvedCheckoutUrl, paymentPreparationDelay } from "../lib/payment-confirmation";
import { Alert, Button, EmptyState, Field, InfiniteListFooter, LoadingBlock, PageError, ProductImage, SafeLink, SelectField, StatusBadge } from "../components/ui";
import "../styles/customer-experience.css";

const emptyAddress: ShippingAddress = {
  recipientName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "IN",
};

const preparationAttempts = 5;

function wait(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));
}

function shortOrderId(orderId: string) {
  return orderId.length > 10 ? `#${orderId.slice(-8).toUpperCase()}` : `#${orderId}`;
}

function orderItemSummary(order: Order) {
  const itemCount = order.items.reduce((total, item) => total + item.quantity, 0);
  const primaryItem = order.items.find((item) => item.productName?.trim());
  if (!primaryItem) return `${itemCount} item${itemCount === 1 ? "" : "s"}`;

  const primaryQuantity = primaryItem.quantity > 1 ? ` × ${primaryItem.quantity}` : "";
  const remainingCount = itemCount - primaryItem.quantity;
  const remaining = remainingCount > 0
    ? ` + ${remainingCount} more item${remainingCount === 1 ? "" : "s"}`
    : "";
  return `${primaryItem.productName!.trim()}${primaryQuantity}${remaining}`;
}

function paymentIsTerminal(payment: Payment | null, order: Order | null = null) {
  if (["REFUND_FAILED", "REFUND_REQUIRES_FULFILMENT_REVIEW"].includes(order?.status ?? "")) return true;
  if (payment?.status === "SUCCESS") return order?.status === "CONFIRMED";
  if (payment) return [ "FAILED", "CANCELLED", "REFUNDED", "REFUND_FAILED", "PAYMENT_EXPIRED", "EXPIRED"].includes(payment.status);
  return ["PAYMENT_FAILED", "PAYMENT_EXPIRED", "CANCELLED", "REFUNDED", "REFUND_REQUIRES_FULFILMENT_REVIEW"].includes(order?.status ?? "");
}

function paymentMessage(payment: Payment | null, order: Order | null) {
  const orderWorkflow = ["CANCELLATION_REQUESTED", "REFUND_REQUESTED", "REFUND_FAILED", "REFUND_REQUIRES_FULFILMENT_REVIEW"].includes(order?.status ?? "");
  const status: string = orderWorkflow ? order!.status : payment?.status ?? order?.status ?? "PENDING";
  switch (status) {
    case "SUCCESS": return { tone: "success" as const, title: "Payment confirmed", text: "Your payment has been confirmed by the platform. Your order status may take a moment to update." };
    case "PAYMENT_FAILED":
    case "FAILED": return { tone: "danger" as const, title: "Payment was not completed", text: "The payment was not confirmed. Review your cart before starting a new checkout." };
    case "CANCELLED": return { tone: "warning" as const, title: "Payment was cancelled", text: "No completed payment is shown for this order. Review your cart before starting again." };
    case "PAYMENT_EXPIRED":
    case "EXPIRED": return { tone: "warning" as const, title: "Payment time expired", text: "This payment can no longer be used. Return to your cart and start a new checkout intent." };
    case "CANCELLATION_REQUESTED": return { tone: "info" as const, title: "Cancellation is being processed", text: "We are confirming payment status before completing your cancellation." };
    case "REFUND_REQUESTED":
    case "REFUND_PROCESSING": return { tone: "info" as const, title: "Refund is being processed", text: "The platform is processing the refund. Check this order later for the authoritative result." };
    case "PARTIALLY_REFUNDED": return { tone: "info" as const, title: "Partial refund completed", text: "A partial refund has been confirmed. Review your order for the remaining payment status." };
    case "REFUNDED": return { tone: "success" as const, title: "Refund completed", text: "The platform shows that this payment has been refunded." };
    case "REFUND_REQUIRES_FULFILMENT_REVIEW":
    case "REFUND_FAILED": return { tone: "danger" as const, title: "Refund needs attention", text: "The refund has not completed. Please use the support path for this order." };
    default: return { tone: "info" as const, title: "Verifying payment", text: "We are waiting for the authoritative order and payment result. A return from the payment provider alone is not confirmation." };
  }
}

function addressFingerprint(address: ShippingAddress) {
  return JSON.stringify({ ...address, recipientName: address.recipientName.trim(), phone: address.phone.trim(), line1: address.line1.trim(), line2: address.line2?.trim(), city: address.city.trim(), state: address.state.trim(), postalCode: address.postalCode.trim(), country: address.country.trim().toUpperCase() });
}

function ProductSummary({ product }: { product: Product }) {
  return <div className="checkout-product"><ProductImage product={product} compact /><div><strong>{product.name}</strong><span>{formatMoney(product.price, product.currency)}</span></div></div>;
}

function CheckoutSteps({ current }: { current: "address" | "payment" | "result" }) {
  const steps = [
    { key: "address", label: "Delivery details", icon: MapPin },
    { key: "payment", label: "Secure payment", icon: CreditCard },
    { key: "result", label: "Confirmation", icon: CheckCircle2 },
  ];
  const activeIndex = steps.findIndex((step) => step.key === current);
  return <ol className="cx-checkout-steps" aria-label="Checkout progress">{steps.map((step, index) => <li key={step.key} className={index === activeIndex ? "cx-step-active" : index < activeIndex ? "cx-step-complete" : ""} aria-current={index === activeIndex ? "step" : undefined}><span className="cx-step-marker">{index < activeIndex ? <Check size={17} aria-hidden="true" /> : <step.icon size={17} aria-hidden="true" />}</span><span><small>Step {index + 1}</small><strong>{step.label}</strong></span></li>)}</ol>;
}

function OrderReceipt({ order, label = "Your order receipt" }: { order: Order; label?: string }) {
  return <aside className="cu-paper-receipt">
    <header className="cu-receipt-header"><span className="cu-receipt-brand">pepekart<span>.</span></span><ReceiptText size={25} strokeWidth={1.4} aria-hidden="true" /></header>
    <div className="cu-receipt-intro"><span className="eyebrow">{label}</span><h2>{shortOrderId(order.id)}</h2><p>{formatDate(order.createdAt)}</p></div>
    <div className="cu-receipt-items">{order.items.map((item, index) => <div key={item.id ?? `${item.productId}-${index}`}><div><strong>{item.productName || "Product"}</strong><span>Qty {item.quantity} · {formatMoney(item.unitPrice, order.currency)} each</span></div><strong>{formatMoney(item.lineTotal ?? item.unitPrice * item.quantity, order.currency)}</strong></div>)}</div>
    <div className="cu-receipt-total"><span>Order total</span><strong>{formatMoney(order.totalAmount, order.currency)}</strong></div>
    <div className="cu-receipt-status"><span>Recorded order status</span><StatusBadge value={order.status} /></div>
    <footer className="cu-receipt-footer"><span>THANK YOU FOR CHOOSING PEPEKART</span><div className="cu-receipt-bars" aria-hidden="true" /></footer>
  </aside>;
}

type CheckoutIntent = { key: string; fingerprint: string };

type CheckoutErrorState = {
  title: string;
  message: string;
  code?: string;
  details: string[];
  traceId?: string;
  refreshCart: boolean;
  refreshCatalogue: boolean;
  requiresNewIntent: boolean;
};

type StructuredCheckoutError = ApiError & { details?: unknown; traceId?: unknown };

const cartReviewErrorCodes = new Set([
  "CHECKOUT_ITEM_PRODUCT_NOT_FOUND",
  "CHECKOUT_ITEM_PRODUCT_UNAVAILABLE",
  "CHECKOUT_ITEM_INSUFFICIENT_STOCK",
  "CHECKOUT_ITEM_QUANTITY_LIMIT",
  "CHECKOUT_ORDER_QUANTITY_LIMIT",
]);

function checkoutDetailLines(details: unknown): string[] {
  if (!Array.isArray(details)) return [];
  return details.flatMap((detail) => {
    if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
      return typeof detail === "string" && detail.trim() ? [detail] : [];
    }
    const value = detail as Record<string, unknown>;
    const message = typeof value.message === "string" && value.message.trim() ? value.message.trim() : null;
    if (message) return [message];
    const product = [value.productName, value.productId].find((candidate): candidate is string => typeof candidate === "string" && Boolean(candidate.trim()));
    const requested = typeof value.requestedQuantity === "number" ? `requested ${value.requestedQuantity}` : null;
    const available = typeof value.availableQuantity === "number" ? `available ${value.availableQuantity}` : null;
    const maximum = value.maximumQuantity ?? value.maxQuantity;
    const limit = typeof maximum === "number" ? `limit ${maximum}` : null;
    const parts = [product, requested, available, limit].filter((part): part is string => Boolean(part));
    return parts.length ? [parts.join(" — ")] : [];
  });
}

function checkoutFailure(error: unknown): CheckoutErrorState {
  const apiError = error instanceof ApiError ? error as StructuredCheckoutError : null;
  const code = apiError?.code?.toUpperCase();
  const details = checkoutDetailLines(apiError?.details);
  const traceId = typeof apiError?.traceId === "string" && apiError.traceId.trim() ? apiError.traceId : undefined;
  const message = messageForError(error);

  if (code === "IDEMPOTENCY_KEY_REUSED") {
    return {
      title: "Start a new checkout attempt",
      message: "This checkout key belongs to a different request. To avoid a duplicate order, it will not be retried automatically. Review the cart and address, then explicitly start a new checkout attempt.",
      code,
      details,
      traceId,
      refreshCart: false,
      refreshCatalogue: false,
      requiresNewIntent: true,
    };
  }

  if (cartReviewErrorCodes.has(code ?? "")) {
    return {
      title: "Cart needs review",
      message: `${message} We refreshed your cart and item details. Update the affected item before submitting again.`,
      code,
      details,
      traceId,
      refreshCart: true,
      refreshCatalogue: true,
      requiresNewIntent: false,
    };
  }

  if (apiError?.retryable) {
    return {
      title: "Checkout is temporarily unavailable",
      message: `${message} It is safe to retry: this checkout keeps the same idempotency key and payload.`,
      code,
      details,
      traceId,
      refreshCart: false,
      refreshCatalogue: false,
      requiresNewIntent: false,
    };
  }

  return {
    title: "Checkout needs attention",
    message,
    code,
    details,
    traceId,
    refreshCart: false,
    refreshCatalogue: false,
    requiresNewIntent: false,
  };
}

function cancellationMessage(order: Order) {
  if (order.status === "CANCELLATION_REQUESTED") {
    return "Your cancellation request is being processed. We are confirming the payment outcome before completing it.";
  }
  if (order.status === "REFUND_FAILED") {
    return "Your refund needs attention. Contact support so the existing refund can be reviewed.";
  }
  if (order.status === "REFUND_REQUESTED" || order.cancellationReasonCode === "REFUND_IN_PROGRESS") {
    return "Your cancellation was accepted and a refund has been requested. The final refund result will appear here after the payment service confirms it.";
  }
  if (order.status === "REFUND_REQUIRES_FULFILMENT_REVIEW") {
    return "This cancellation needs fulfilment review before the refund can be completed. The support team will use the order history to resolve it.";
  }
  if (order.status === "PAYMENT_EXPIRED") {
    return "The payment window expired. This order can no longer be paid; review your cart before starting a new checkout.";
  }
  if (order.cancellationReasonCode === "ORDER_CANCELLATION_NOT_ALLOWED") {
    return "This order is no longer eligible for self-service cancellation.";
  }
  return "This order is not currently eligible for cancellation.";
}

export function CheckoutPage() {
  const { accessToken } = useAuth();
  const { cart, isLoading: cartLoading, error: cartError, refresh: refreshCart } = useCart();
  const navigate = useNavigate();
  const [address, setAddress] = useState<ShippingAddress>(emptyAddress);
  const [submitting, setSubmitting] = useState(false);
  const [checkoutError, setCheckoutError] = useState<CheckoutErrorState | null>(null);
  const [createdOrder, setCreatedOrder] = useState<Order | null>(null);
  const intent = useRef<CheckoutIntent | null>(null);
  const cartSignature = (cart?.items ?? []).map((item) => `${item.productId}:${item.quantity}`).sort().join("|");
  const { data: products, loading: productLoading, reload: refreshProducts } = useResource(
    () => Promise.all((cart?.items ?? []).map((item) => api.products.byId(item.productId).catch(() => null))).then((values) => values.filter((value): value is Product => Boolean(value))),
    [cartSignature],
  );
  const productsById = useMemo(() => new Map((products ?? []).map((product) => [product.id, product])), [products]);
  const estimatedTotal = (cart?.items ?? []).reduce((total, item) => total + (productsById.get(item.productId)?.price ?? 0) * item.quantity, 0);
  const currencies = new Set((cart?.items ?? []).map((item) => productsById.get(item.productId)?.currency ?? "INR"));
  const currency: string = currencies.values().next().value ?? "INR";
  const mixedCurrencies = currencies.size > 1;

  function setAddressValue(field: keyof ShippingAddress, value: string) {
    setAddress((current) => ({ ...current, [field]: value }));
    setCheckoutError(null);
    // The request fingerprint below decides whether a new key is needed. Clearing it
    // for every keystroke would unexpectedly discard a safe retry when a user edits
    // and then restores the same address.
  }

  function refreshCheckoutData(includeCatalogue: boolean) {
    const reloads = [refreshCart(), ...(includeCatalogue ? [refreshProducts()] : [])];
    void Promise.allSettled(reloads);
  }

  function startNewCheckoutIntent() {
    // This is intentionally the only same-payload path that discards a conflicted key.
    intent.current = null;
    setCheckoutError(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!accessToken || !cart?.items.length || mixedCurrencies) return;
    setSubmitting(true);
    setCheckoutError(null);
    const fingerprint = `${cartSignature}|${addressFingerprint(address)}|${currency}`;
    if (!intent.current || intent.current.fingerprint !== fingerprint) intent.current = { key: createIdempotencyKey(), fingerprint };
    try {
      const order = await api.orders.create(accessToken, cart.items.map((item) => ({ productId: item.productId, quantity: item.quantity })), address, currency, intent.current.key);
      setCreatedOrder(order);
    } catch (error) {
      const failure = checkoutFailure(error);
      setCheckoutError(failure);
      if (failure.refreshCart) refreshCheckoutData(failure.refreshCatalogue);
    } finally {
      setSubmitting(false);
    }
  }

  if (cartLoading) return <LoadingBlock label="Loading checkout" />;
  if (createdOrder && accessToken) return <PaymentPreparation order={createdOrder} token={accessToken} onReviewOrder={() => navigate(`/orders/${createdOrder.id}`, { replace: true })} />;
  if (cartError) return <PageError message={cartError} retry={() => void refreshCart()} />;
  if (!cart?.items.length) return <EmptyState title="Your cart is empty" message="Add an item before beginning checkout." action={<Link className="button button-primary" to="/">Browse products</Link>} />;

  return <section className="checkout-page customer-experience customer-editorial">
    <header className="cu-flow-masthead"><div><SafeLink className="cu-back-link" to="/cart"><ArrowLeft size={15} aria-hidden="true" /> Back to the bag</SafeLink><span className="eyebrow">The final few details</span><h1>Good things.<br /><em>Coming your way.</em></h1><p>Tell us where your finds belong.</p></div><CheckoutSteps current="address" /></header>
    {mixedCurrencies ? <Alert tone="warning" title="Cart needs review">This cart contains items in more than one currency. Review the cart before checkout can continue.</Alert> : null}
    {checkoutError ? <Alert tone={checkoutError.requiresNewIntent ? "warning" : "danger"} title={checkoutError.title} action={<div className="button-row">{checkoutError.requiresNewIntent ? <Button variant="secondary" onClick={startNewCheckoutIntent}>Start new checkout attempt</Button> : null}{checkoutError.refreshCart ? <Button variant="secondary" onClick={() => refreshCheckoutData(checkoutError.refreshCatalogue)}>Refresh cart and items</Button> : null}</div>}><div><p>{checkoutError.message}</p>{checkoutError.details.length ? <ul>{checkoutError.details.map((detail, index) => <li key={`${detail}-${index}`}>{detail}</li>)}</ul> : null}{checkoutError.code ? <p className="muted">Error code: {checkoutError.code}{checkoutError.traceId ? ` · Support reference: ${checkoutError.traceId}` : ""}</p> : null}</div></Alert> : null}
    <div className="cu-checkout-grid">
      <form className="cu-delivery-form" onSubmit={submit}>
        <fieldset className="cu-form-section"><legend><span>01</span> The recipient</legend><p>Who are we delivering to?</p><div className="form-grid"><Field label="Recipient name" autoComplete="shipping name" value={address.recipientName} onChange={(event) => setAddressValue("recipientName", event.target.value)} required /><Field label="Phone number" type="tel" autoComplete="shipping tel" value={address.phone} onChange={(event) => setAddressValue("phone", event.target.value)} required /></div></fieldset>
        <fieldset className="cu-form-section"><legend><span>02</span> The destination</legend><p>This address will be used for this order.</p><div className="cu-address-fields"><Field label="Address line 1" autoComplete="shipping address-line1" value={address.line1} onChange={(event) => setAddressValue("line1", event.target.value)} required /><Field label="Address line 2 (optional)" autoComplete="shipping address-line2" value={address.line2 ?? ""} onChange={(event) => setAddressValue("line2", event.target.value)} /><div className="form-grid"><Field label="City" autoComplete="shipping address-level2" value={address.city} onChange={(event) => setAddressValue("city", event.target.value)} required /><Field label="State / region" autoComplete="shipping address-level1" value={address.state} onChange={(event) => setAddressValue("state", event.target.value)} required /></div><div className="form-grid"><Field label="Postal code" autoComplete="shipping postal-code" value={address.postalCode} onChange={(event) => setAddressValue("postalCode", event.target.value)} required /><Field label="Country code" autoComplete="shipping country" maxLength={2} value={address.country} onChange={(event) => setAddressValue("country", event.target.value.toUpperCase())} required /></div></div></fieldset>
        <div className="cu-checkout-next"><div><span className="cu-section-number">03</span><h2>Next, make it yours.</h2><p>We’ll check prices and availability, then prepare your payment.</p></div><Button type="submit" loading={submitting} disabled={mixedCurrencies || productLoading || checkoutError?.requiresNewIntent}>Place order &amp; continue <ArrowRight size={18} aria-hidden="true" /></Button></div>
        <div className="cu-trust-line"><LockKeyhole size={15} aria-hidden="true" /><span>Payment happens with your payment provider after this step.</span></div>
      </form>
      <aside className="cu-paper-receipt cu-bag-receipt"><header className="cu-receipt-header"><span className="cu-receipt-brand">pepekart<span>.</span></span><ShoppingBag size={24} strokeWidth={1.4} aria-hidden="true" /></header><div className="cu-receipt-intro"><span className="eyebrow">Your selection</span><h2>The bag.</h2><p>{cart.items.reduce((total, item) => total + item.quantity, 0)} items, chosen by you.</p></div>{productLoading ? <LoadingBlock label="Loading item details" /> : <div className="checkout-product-list">{cart.items.map((item) => { const product = productsById.get(item.productId); return product ? <div key={item.itemId} className="checkout-product-row"><ProductSummary product={product} /><span className="cu-quantity">× {item.quantity}</span></div> : <div key={item.itemId} className="checkout-product-row"><span>Product details unavailable</span><span className="cu-quantity">× {item.quantity}</span></div>; })}</div>}<div className="cu-receipt-total"><span>Estimated total</span><strong>{formatMoney(estimatedTotal, currency)}</strong></div><p className="cu-receipt-note">Final prices and total are confirmed when your order is created.</p><footer className="cu-bag-footer"><ShieldCheck size={20} aria-hidden="true" /><span>Review your bag.<br /><strong>We’ll handle the next step.</strong></span></footer></aside>
    </div>
  </section>;
}

function PaymentPreparation({ order, token, onReviewOrder }: { order: Order; token: string; onReviewOrder: () => void }) {
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [phase, setPhase] = useState<"preparing" | "ready" | "unavailable">("preparing");
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);
  const preparing = useRef(false);
  const [redirecting, setRedirecting] = useState(false);

  const prepare = useCallback(async () => {
    if (preparing.current) return;
    preparing.current = true;
    setPhase("preparing");
    setError(null);
    try {
    for (let attempt = 0; attempt < preparationAttempts; attempt += 1) {
      try {
        const session = await api.payments.checkoutSession(token, order.id);
        setPayment(session);
        if (session.checkoutUrl) {
          setPhase("ready");
          return;
        }
        setError("Payment preparation completed, but no secure payment link was supplied. Review the order status before trying again.");
        setPhase("unavailable");
        return;
      } catch (caught) {
        const delay = caught instanceof ApiError ? paymentPreparationDelay(caught, attempt) : null;
        if (delay !== null) {
          await wait(delay);
          continue;
        }
        setError(messageForError(caught));
        setPhase("unavailable");
        return;
      }
    }
    setError("Payment is still being prepared. Do not create another order; check this order again shortly.");
    setPhase("unavailable");
    } finally { preparing.current = false; }
  }, [order.id, token]);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void prepare();
  }, [prepare]);

  function continueToProvider() {
    const checkoutUrl = payment?.checkoutUrl;
    if (!checkoutUrl || redirecting) return;
    try {
      const target = approvedCheckoutUrl(checkoutUrl, payment?.expiresAt, { developmentOrigin: useMocks ? window.location.origin : undefined, sandbox: import.meta.env.DEV && payment?.provider?.toUpperCase() === "SANDBOX" });
      setRedirecting(true);
      window.location.assign(target);
    } catch {
      setError("The secure payment link could not be opened. Check your order status and try again.");
      setPhase("unavailable");
    }
  }

  return <section className="customer-experience customer-editorial cu-payment-preparation">
    <div className="cu-flow-topline"><span className="eyebrow">Pepekart checkout</span><CheckoutSteps current="payment" /></div>
    <div className="cu-payment-split">
      <div className="cu-provider-stage">
        <div className="cu-payment-art" aria-hidden="true"><div className="cu-art-ring" /><div className="cu-art-card"><span>PEPEKART</span><CreditCard size={44} strokeWidth={1.15} /><div><span>THE NEXT STEP</span><ArrowUpRight size={23} /></div></div><span className="cu-art-lock"><LockKeyhole size={23} strokeWidth={1.5} /></span><span className="cu-art-dot" /></div>
        <span className="eyebrow">02 / Secure payment</span>
        <h1>{phase === "ready" ? <>One last step.<br /><em>Then it’s yours.</em></> : <>A moment,<br /><em>while we get ready.</em></>}</h1>
        {phase === "preparing" ? <div className="cu-stage-message"><LoadingBlock label="Preparing your payment" /><p>Keep this page open. Your order has been created; please don’t place another one while we prepare payment.</p></div> : null}
        {phase === "ready" ? <><p className="cu-stage-intro">Your order is created. Continue to your payment provider to complete checkout.</p><div className="cu-provider-destination"><span className="cu-provider-icon"><CreditCard size={22} aria-hidden="true" /></span><div><span>Continue with</span><strong>{payment?.provider || "Your payment provider"}</strong></div><ArrowUpRight size={20} aria-hidden="true" /></div><Button className="cu-stage-primary" disabled={redirecting} onClick={continueToProvider}>Continue to payment <ArrowRight size={18} aria-hidden="true" /></Button><Button variant="ghost" onClick={onReviewOrder}>Review order instead</Button><div className="cu-trust-line"><ShieldCheck size={16} aria-hidden="true" /><span>We verify the payment result when you return.</span></div></> : null}
        {phase === "unavailable" ? <><Alert tone="warning" title="Payment is not ready yet">{error}</Alert><div className="cu-stage-actions"><Button variant="secondary" onClick={() => { started.current = true; void prepare(); }}><RefreshCw size={16} aria-hidden="true" />Check again</Button><Button variant="ghost" onClick={() => navigate(`/payment/return?orderId=${encodeURIComponent(order.id)}`)}>View payment status</Button><Button variant="ghost" onClick={onReviewOrder}>View order</Button></div></> : null}
      </div>
      <OrderReceipt order={order} label="Ready for the next step" />
    </div>
  </section>;
}

type VerificationState = { order: Order | null; payment: Payment | null; loading: boolean; error: string | null; stopped: boolean; attempts: number };

export function PaymentReturnPage() {
  const { accessToken } = useAuth();
  const { refresh: refreshCart } = useCart();
  const [search] = useSearchParams();
  const orderId = search.get("orderId")?.trim() ?? "";
  const [state, setState] = useState<VerificationState>({ order: null, payment: null, loading: Boolean(orderId), error: null, stopped: false, attempts: 0 });
  const [verificationRun, setVerificationRun] = useState(0);

  const load = useCallback(async (cancelled: () => boolean = () => false) => {
    if (!accessToken || !orderId) return null;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const order = await api.orders.byId(accessToken, orderId);
      if (cancelled()) return null;
      let payment: Payment | null = null;
      try { payment = await api.payments.byOrder(accessToken, orderId); }
      catch (caught) { if (!(caught instanceof ApiError && caught.code === "PAYMENT_PREPARING")) throw caught; }
      const result = { order, payment, terminal: paymentIsTerminal(payment, order) };
      if (cancelled()) return null;
      setState((current) => ({ ...current, order, payment, loading: false, error: null }));
      return result;
    } catch (caught) {
      if (cancelled()) return null;
      setState((current) => ({ ...current, loading: false, error: messageForError(caught) }));
      return { order: null, payment: null, terminal: !(caught instanceof ApiError && caught.retryable) };
    }
  }, [accessToken, orderId]);

  const needsNewCheckout = ["FAILED", "CANCELLED", "PAYMENT_EXPIRED", "EXPIRED", "PAYMENT_FAILED"].includes(String(state.payment?.status ?? state.order?.status ?? ""));

  useEffect(() => {
    if (state.payment?.status === "SUCCESS") void refreshCart();
  }, [refreshCart, state.payment?.status]);

  useEffect(() => {
    if (needsNewCheckout) void refreshCart();
  }, [needsNewCheckout, refreshCart]);

  useEffect(() => {
    if (!orderId || !accessToken) return;
    let cancelled = false;
    setState(current => ({ ...current, stopped: false, attempts: 0, error: null,
      order: current.order?.id === orderId ? current.order : null,
      payment: current.order?.id === orderId ? current.payment : null }));
    void pollPaymentConfirmation(() => load(() => cancelled), () => cancelled,
      attempts => setState(current => ({ ...current, attempts })),
      () => setState(current => ({ ...current, stopped: true })));
    return () => { cancelled = true; };
  }, [accessToken, load, orderId, verificationRun]);

  if (!orderId) return <EmptyState title="Payment status unavailable" message="Open this page from an order or from the approved payment return flow." action={<Link className="button button-primary" to="/orders">View orders</Link>} />;
  if (state.loading && !state.order) return <LoadingBlock label="Verifying your payment" />;
  if (state.error && !state.order) return <PageError message={state.error} retry={() => setVerificationRun(run => run + 1)} />;
  const status = paymentMessage(state.payment, state.order);
  const orderConfirmed = state.payment?.status === "SUCCESS" && state.order?.status === "CONFIRMED";
  const resultTone = orderConfirmed ? "success" : status.tone === "success" ? "info" : status.tone;
  return <section className={`customer-experience customer-editorial cu-payment-return cu-result-${resultTone}`}>
    <div className="cu-flow-topline"><SafeLink className="cu-back-link" to="/orders"><ArrowLeft size={15} aria-hidden="true" /> Your orders</SafeLink><span className="eyebrow">Pepekart / Payment update</span></div>
    <div className="cu-result-canvas">
      <div className="cu-result-story">
        <div className="cu-result-emblem" aria-hidden="true">{orderConfirmed ? <Check size={88} strokeWidth={1.15} /> : resultTone === "danger" || resultTone === "warning" ? <CircleAlert size={79} strokeWidth={1.15} /> : <Clock3 size={79} strokeWidth={1.15} />}<span className="cu-result-emblem-orbit" /></div>
        <span className="eyebrow">{orderConfirmed ? "03 / Order confirmed" : "Your latest update"}</span>
        <h1>{orderConfirmed ? <>A good choice.<br /><em>Now, all yours.</em></> : status.title}</h1>
        <p className="cu-result-description">{orderConfirmed ? "Your payment is received and your order is confirmed. Here’s everything you need for what comes next." : status.text}</p>
        {state.error ? <Alert tone="warning" title="Status update delayed">{state.error}</Alert> : null}
        {!paymentIsTerminal(state.payment, state.order) && !state.error ? <div className="cu-result-polling"><LoadingBlock label={state.stopped ? "Automatic checks paused" : "Checking your payment result"} /><p>{state.stopped ? "Refresh whenever you’re ready to check again." : "We’re waiting for the payment provider’s confirmed result."}</p></div> : null}
        <div className="cu-recorded-status"><div><span>Order</span><StatusBadge value={state.order?.status} /></div><div><span>Payment</span><StatusBadge value={state.payment?.status === "REQUIRES_CUSTOMER_ACTION" ? "AWAITING_CONFIRMATION" : state.payment?.status ?? "PREPARING"} /></div></div>
      </div>
      {state.order ? <OrderReceipt order={state.order} label={orderConfirmed ? "Your confirmed order" : "Your order receipt"} /> : null}
    </div>
    <nav className="cu-next-actions" aria-label="Next steps"><div><span className="eyebrow">What’s next?</span><h2>{orderConfirmed ? "Keep the good things going." : "Your next step."}</h2></div>{state.order ? <Link className="cu-next-action cu-next-action-primary" to={`/orders/${state.order.id}`}><Package size={22} strokeWidth={1.5} aria-hidden="true" /><span><strong>View your order</strong><small>Details &amp; latest status</small></span><ArrowUpRight size={22} aria-hidden="true" /></Link> : null}<Button className="cu-next-action" variant="secondary" disabled={state.loading} onClick={() => setVerificationRun((run) => run + 1)}><RefreshCw size={21} strokeWidth={1.5} aria-hidden="true" /><span><strong>Check again</strong><small>Refresh payment status</small></span><ArrowUpRight size={21} aria-hidden="true" /></Button>{needsNewCheckout ? <Link className="cu-next-action" to="/cart"><ShoppingBag size={22} strokeWidth={1.5} aria-hidden="true" /><span><strong>Review your bag</strong><small>Start a new checkout</small></span><ArrowUpRight size={21} aria-hidden="true" /></Link> : <Link className="cu-next-action" to="/orders"><ReceiptText size={22} strokeWidth={1.5} aria-hidden="true" /><span><strong>All your orders</strong><small>Your purchase collection</small></span><ArrowUpRight size={21} aria-hidden="true" /></Link>}</nav>
  </section>;
}

const orderStatusOptions = ["", "PENDING", "CONFIRMED", "PAYMENT_FAILED", "PAYMENT_EXPIRED", "CANCELLATION_REQUESTED", "CANCELLED", "REFUND_REQUESTED", "REFUND_FAILED", "PARTIALLY_REFUNDED", "REFUNDED", "REFUND_REQUIRES_FULFILMENT_REVIEW"];

export function OrdersPage() {
  const { accessToken } = useAuth();
  const [params, setParams] = useSearchParams();
  const filters = new URLSearchParams(params);
  filters.delete("page"); filters.delete("size");
  const query = filters.toString();
  const selectedStatus = params.get("status") ?? "";
  const fetchPage = useCallback((page: number) => {
    const request = new URLSearchParams(query);
    request.set("page", String(page)); request.set("size", "10");
    return api.orders.list(accessToken ?? "", request.toString()).then(toPage<Order>);
  }, [accessToken, query]);
  const orders = useInfinitePage(fetchPage);
  const { items, loading, error, reload } = orders;
  function setStatus(status: string) { const next = new URLSearchParams(params); if (status) next.set("status", status); else next.delete("status"); next.delete("page"); next.delete("size"); setParams(next); }
  return <section className="orders-page customer-experience customer-editorial">
    <header className="cu-account-masthead"><div><span className="eyebrow">Your account / Orders</span><h1>The things<br /><em>you’ve chosen.</em></h1></div><div className="cu-masthead-aside"><p>A little history of your good taste. <br />Every order and its latest details, right here.</p><Link className="cu-text-link" to="/">Find your next favourite <ArrowUpRight size={16} aria-hidden="true" /></Link></div></header>
    <div className="cu-orders-workspace">
      <aside className="cu-account-menu"><span className="eyebrow">Your space</span><nav aria-label="Your account"><Link className="is-current" aria-current="page" to="/orders"><ShoppingBag size={18} aria-hidden="true" />Orders<ChevronRight size={16} aria-hidden="true" /></Link><Link to="/account"><UserRound size={18} aria-hidden="true" />Account &amp; settings<ChevronRight size={16} aria-hidden="true" /></Link></nav><div className="cu-menu-note"><Sparkles size={23} strokeWidth={1.25} aria-hidden="true" /><p>Good finds.<br /><strong>All in one place.</strong></p></div></aside>
      <div className="cu-orders-content">
        <div className="cu-orders-controls"><div className="cu-status-tabs" aria-label="Order status filters">{[{ value: "", label: "All orders" }, { value: "CONFIRMED", label: "Confirmed" }, { value: "PENDING", label: "Pending" }].map((filter) => <button type="button" key={filter.value} aria-pressed={selectedStatus === filter.value} className={selectedStatus === filter.value ? "is-active" : ""} onClick={() => setStatus(filter.value)}>{filter.label}</button>)}</div><div className="cu-status-select"><SlidersHorizontal size={17} aria-hidden="true" /><SelectField label="Status" value={selectedStatus} onChange={(event) => setStatus(event.target.value)}>{orderStatusOptions.map((value) => <option key={value} value={value}>{value ? value.replace(/_/g, " ") : "All statuses"}</option>)}</SelectField></div></div>
        <div className="cu-orders-count"><span>{loading ? "Finding your orders" : `${orders.totalElements} order${orders.totalElements === 1 ? "" : "s"}`}</span><span>Current recorded status</span></div>
        {loading ? <LoadingBlock label="Loading your orders" /> : error ? <PageError message={error} retry={() => void reload()} /> : items.length ? <><div className="cu-purchase-list">{items.map((order, index) => <article className="cu-purchase-row" key={order.id}><div className={`cu-purchase-cover cu-purchase-cover-${index % 3}`} aria-hidden="true"><Package size={41} strokeWidth={1.15} /><span>PEPEKART</span></div><div className="cu-purchase-description"><div className="cu-purchase-meta"><span>{shortOrderId(order.id)}</span><time dateTime={order.createdAt}>{formatDate(order.createdAt)}</time></div><h2>{orderItemSummary(order)}</h2><p>{order.items.reduce((total, item) => total + item.quantity, 0)} item{order.items.reduce((total, item) => total + item.quantity, 0) === 1 ? "" : "s"} · <span>{formatMoney(order.totalAmount, order.currency)}</span></p><StatusBadge value={order.status} /></div><Link className="cu-order-open" to={`/orders/${order.id}`} aria-label={`View order ${shortOrderId(order.id)}`}><ArrowUpRight size={23} strokeWidth={1.4} aria-hidden="true" /><span>View order</span></Link></article>)}</div><InfiniteListFooter {...orders} loadedCount={items.length} noun="orders" /></> : <EmptyState title={selectedStatus ? "No orders match this status" : "The beginning of something good."} message={selectedStatus ? "Try another order status or view all orders." : "Your orders will appear here once you begin checkout. Find your first favourite."} action={selectedStatus ? <Button onClick={() => setStatus("")}>Clear filter</Button> : <Link className="button button-primary" to="/">Explore Pepekart <ArrowRight size={17} aria-hidden="true" /></Link>} />}
      </div>
    </div>
  </section>;
}

type OrderDetailData = { order: Order; payment: Payment | null };

export function OrderDetailPage() {
  const { accessToken } = useAuth();
  const { orderId = "" } = useParams();
  const [confirmCancellation, setConfirmCancellation] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionNotice, setActionNotice] = useState<string | null>(null);
  const { data, loading, error, reload, setData } = useResource(async () => {
    const order = await api.orders.byId(accessToken ?? "", orderId);
    let payment: Payment | null = null;
    try { payment = await api.payments.byOrder(accessToken ?? "", orderId); }
    catch (caught) { if (!(caught instanceof ApiError && caught.status === 404)) throw caught; }
    return { order, payment };
  }, [accessToken, orderId]);
  const order = data?.order;
  async function cancelOrder() {
    if (!accessToken || !order) return;
    setCancelling(true);
    setActionError(null);
    setActionNotice(null);
    try {
      const updated = await api.orders.cancel(accessToken, order.id);
      setData((current) => current ? { ...current, order: updated } : { order: updated, payment: null });
      setActionNotice(updated.status === "REFUND_REQUESTED"
        ? cancellationMessage(updated)
        : "Your cancellation request was accepted. Refresh this order to see the latest authoritative status.");
      setConfirmCancellation(false);
    } catch (caught) {
      setActionError(messageForError(caught));
    } finally {
      setCancelling(false);
    }
  }
  if (loading) return <LoadingBlock label="Loading order" />;
  if (error || !order) return <EmptyState title="Order not found" message="This order may no longer be available in your account." action={<Link className="button button-primary" to="/orders">Return to orders</Link>} />;
  const paymentStatus = paymentMessage(data?.payment ?? null, order);
  const shipping = order.shippingAddress;
  const lifecycleNotice = ["CANCELLATION_REQUESTED", "REFUND_REQUESTED", "REFUND_FAILED", "REFUND_REQUIRES_FULFILMENT_REVIEW", "PAYMENT_EXPIRED"].includes(order.status) || Boolean(order.cancellationReasonCode)
    ? cancellationMessage(order)
    : null;
  return <section className="order-detail-page customer-experience customer-editorial">
    <header className="cu-order-detail-masthead"><div><SafeLink className="cu-back-link" to="/orders"><ArrowLeft size={15} aria-hidden="true" /> Back to your orders</SafeLink><span className="eyebrow">Your purchase, in detail</span><h1>Order <em>{shortOrderId(order.id)}.</em></h1><p>Placed on {formatDate(order.createdAt)}</p></div><span className="cu-order-detail-mark" aria-hidden="true"><Package size={65} strokeWidth={1} /></span></header>
    {actionError ? <Alert tone="danger" title="Cancellation needs attention">{actionError}</Alert> : null}
    {actionNotice ? <Alert tone="info" title="Cancellation update">{actionNotice}</Alert> : null}
    {lifecycleNotice && !actionNotice ? <Alert tone={order.status === "REFUND_REQUIRES_FULFILMENT_REVIEW" ? "warning" : "info"} title={order.status === "REFUND_REQUESTED" ? "Refund in progress" : "Order update"}>{lifecycleNotice}</Alert> : null}
    <div className="cu-order-detail-grid">
      <OrderReceipt order={order} />
      <div className="cu-order-story">
        <section className="cu-status-story"><div className="cu-story-heading"><span className="eyebrow">The order story</span><h2>Here’s where things stand.</h2></div><div className="cu-status-fact"><span className="cu-fact-icon"><ReceiptText size={20} aria-hidden="true" /></span><div><span className="eyebrow">Order created</span><strong>{formatDate(order.createdAt)}</strong><p>{order.items.reduce((total, item) => total + item.quantity, 0)} items in your selection.</p></div></div><div className="cu-status-fact"><span className="cu-fact-icon"><Package size={20} aria-hidden="true" /></span><div><span className="eyebrow">Current order status</span><StatusBadge value={order.status} /><p>{order.status === "CONFIRMED" ? "Your order is confirmed. Any new updates will appear here." : "This is the latest status recorded for your order."}</p></div></div><div className="cu-status-fact"><span className="cu-fact-icon"><CreditCard size={20} aria-hidden="true" /></span><div><span className="eyebrow">Payment update</span><StatusBadge value={data?.payment?.status ?? "PREPARING"} /><p>{paymentStatus.text}</p><Link className="cu-text-link" to={`/payment/return?orderId=${encodeURIComponent(order.id)}`}>Review payment status <ArrowUpRight size={16} aria-hidden="true" /></Link></div></div></section>
        <section className="cu-order-address"><div><MapPin size={21} strokeWidth={1.5} aria-hidden="true" /><span className="eyebrow">Delivery details</span></div>{shipping ? <address><strong>{shipping.recipientName}</strong><span>{shipping.line1}{shipping.line2 ? <>, {shipping.line2}</> : null}</span><span>{shipping.city}, {shipping.state} {shipping.postalCode}</span><span>{shipping.country} · {shipping.phone}</span></address> : <p>The delivery address is not available for this order.</p>}</section>
        <section className="cu-order-management"><div><span className="eyebrow">Need to make a change?</span><h2>Manage this order.</h2></div>{order.cancelAllowed ? <>{confirmCancellation ? <div className="cu-cancel-confirmation"><Alert tone="warning" title="Cancel this order?">This sends a cancellation request. For a paid order, the platform requests a refund and records the final outcome asynchronously.</Alert><div className="button-row"><Button variant="danger" loading={cancelling} onClick={() => void cancelOrder()}>Yes, request cancellation</Button><Button variant="ghost" disabled={cancelling} onClick={() => setConfirmCancellation(false)}>Keep order</Button></div></div> : <Button variant="secondary" onClick={() => setConfirmCancellation(true)}>Request cancellation</Button>}</> : <p>{cancellationMessage(order)}</p>}<Button variant="ghost" onClick={() => void reload()}><RefreshCw size={16} aria-hidden="true" />Refresh order</Button></section>
      </div>
    </div>
  </section>;
}

type SessionRecord = BrowserSession;

function sessionName(session: SessionRecord) {
  return session.deviceName?.trim() || session.userAgent?.trim() || "Browser session";
}

function firstName(name?: string) {
  return name?.trim().split(/\s+/)[0] || "there";
}

export function AccountPage() {
  const { user, accessToken, refreshProfile, consumeSession } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState(user?.name ?? "");
  const [newEmail, setNewEmail] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [emailMessage, setEmailMessage] = useState<string | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [accountMessage, setAccountMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState<"profile" | "email" | "password" | "session" | "delete" | null>(null);
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const [sessionToRevoke, setSessionToRevoke] = useState<SessionRecord | null>(null);
  const { data: sessions, loading: sessionsLoading, error: sessionsError, reload: reloadSessions } = useResource(() => api.users.sessions(accessToken ?? ""), [accessToken]);

  useEffect(() => { setName(user?.name ?? ""); }, [user?.name]);

  async function updateProfile(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setBusy("profile"); setProfileMessage(null);
    try { await api.users.update(accessToken, name.trim()); await refreshProfile(); setProfileMessage("Your profile name was updated."); }
    catch (caught) { setProfileMessage(messageForError(caught)); }
    finally { setBusy(null); }
  }
  async function requestEmailChange(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setBusy("email"); setEmailMessage(null);
    try { await api.users.requestEmailChange(accessToken, newEmail); setEmailMessage("Check the new email address for a one-time confirmation link. Delivery can take a few minutes."); }
    catch (caught) { setEmailMessage(messageForError(caught)); }
    finally { setBusy(null); }
  }
  async function changePassword(event: FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    if (newPassword.length < 12) { setPasswordMessage("Use at least 12 characters for your new password."); return; }
    if (newPassword !== confirmPassword) { setPasswordMessage("The new passwords do not match."); return; }
    setBusy("password"); setPasswordMessage(null);
    try {
      await api.users.changePassword(accessToken, currentPassword, newPassword);
      consumeSession();
      navigate("/login", { replace: true, state: { notice: "Your password changed. Sign in again to continue." } });
    } catch (caught) { setPasswordMessage(messageForError(caught)); }
    finally { setBusy(null); }
  }
  async function revokeSession() {
    if (!accessToken || !sessionToRevoke) return;
    setBusy("session");
    try { await api.users.revokeSession(accessToken, sessionToRevoke.id); setSessionToRevoke(null); await reloadSessions(); }
    catch (caught) { setAccountMessage(messageForError(caught)); setSessionToRevoke(null); }
    finally { setBusy(null); }
  }
  async function deleteAccount() {
    if (!accessToken || deleteConfirmation !== "DELETE") return;
    setBusy("delete"); setAccountMessage(null);
    try { await api.users.delete(accessToken); consumeSession(); navigate("/", { replace: true, state: { notice: "Your account was deleted." } }); }
    catch (caught) { setAccountMessage(messageForError(caught)); }
    finally { setBusy(null); }
  }

  const roles = user?.roles?.length ? user.roles : user?.role ? [user.role] : [];
  const emailUnchanged = newEmail.trim().toLowerCase() === user?.email.toLowerCase();

  return <section className="account-page account-experience customer-experience customer-editorial">
    <header className="cu-profile-masthead"><span className="cu-profile-avatar" aria-hidden="true">{user?.name.slice(0, 1).toUpperCase() || "P"}<Sparkles size={20} strokeWidth={1.3} /></span><div><span className="eyebrow">Your account / The personal details</span><h1>Hello, <em>{firstName(user?.name)}.</em></h1><p>A space that’s entirely yours.</p></div><div className="cu-profile-membership"><span className="eyebrow">Your Pepekart identity</span><strong>{user?.email || "Your account"}</strong><div><StatusBadge value={user?.status} />{roles.map((role) => <span className="cu-role-label" key={role}>{role.toLowerCase()}</span>)}</div></div></header>
    <div className="cu-settings-overview"><a href="#account-profile"><UserRound size={24} strokeWidth={1.4} aria-hidden="true" /><span><small>The essentials</small><strong>Personal details</strong></span><ArrowUpRight size={21} aria-hidden="true" /></a><a href="#account-security"><ShieldCheck size={25} strokeWidth={1.4} aria-hidden="true" /><span><small>Stay in control</small><strong>Password &amp; security</strong></span><ArrowUpRight size={21} aria-hidden="true" /></a><a href="#account-sessions"><Laptop size={25} strokeWidth={1.4} aria-hidden="true" /><span><small>{sessionsLoading || sessionsError ? "Manage access" : `${sessions?.length ?? 0} active browser sessions`}</small><strong>Your devices</strong></span><ArrowUpRight size={21} aria-hidden="true" /></a></div>
    {accountMessage ? <Alert tone="danger" title="Account action needs attention">{accountMessage}</Alert> : null}
    <div className="cu-settings-hub">
      <aside className="cu-account-menu"><span className="eyebrow">Account settings</span><nav aria-label="Account settings"><a href="#account-profile"><UserRound size={17} aria-hidden="true" />Personal details</a><a href="#account-email"><Mail size={17} aria-hidden="true" />Email address</a><a href="#account-security"><KeyRound size={17} aria-hidden="true" />Security</a><a href="#account-sessions"><Laptop size={17} aria-hidden="true" />Devices &amp; sessions</a><Link to="/orders"><ShoppingBag size={17} aria-hidden="true" />Your orders<ArrowUpRight size={16} aria-hidden="true" /></Link></nav><div className="cu-menu-note"><LockKeyhole size={23} strokeWidth={1.25} aria-hidden="true" /><p>Your details.<br /><strong>Your control.</strong></p></div></aside>
      <div className="cu-settings-content">
        <section className="cu-setting-section" id="account-profile"><div className="cu-setting-label"><span className="cu-section-number">01</span><h2>The essentials.</h2><p>The name you go by and the address we use to reach you.</p></div><div className="cu-setting-body"><form className="form-stack" onSubmit={updateProfile}><Field label="Full name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} required />{profileMessage ? <Alert tone={profileMessage.startsWith("Your profile") ? "success" : "danger"}>{profileMessage}</Alert> : null}<Button type="submit" loading={busy === "profile"}>Save name <Check size={16} aria-hidden="true" /></Button></form><div className="cu-identity-readout"><Mail size={19} strokeWidth={1.4} aria-hidden="true" /><div><span>Your email address</span><strong>{user?.email || "—"}</strong><a href="#account-email">Change email <ArrowUpRight size={13} aria-hidden="true" /></a></div></div></div></section>
        <section className="cu-setting-section" id="account-email"><div className="cu-setting-label"><span className="cu-section-number">02</span><h2>A new address?</h2><p>We’ll send a one-time confirmation link to your new email.</p></div><form className="form-stack cu-setting-body" onSubmit={requestEmailChange}><Field label="New email address" type="email" autoComplete="email" value={newEmail} onChange={(event) => setNewEmail(event.target.value)} hint="You will remain signed in until the email link is confirmed." required />{emailMessage ? <Alert tone={emailMessage.startsWith("Check") ? "success" : "danger"}>{emailMessage}</Alert> : null}<Button type="submit" loading={busy === "email"} disabled={!newEmail.trim() || emailUnchanged}>Send confirmation <ArrowRight size={16} aria-hidden="true" /></Button></form></section>
        <section className="cu-setting-section cu-security-section" id="account-security"><div className="cu-setting-label"><span className="cu-section-number">03</span><h2>Keep it yours.</h2><p>A long, unique password makes a difference. Changing it signs out every device.</p><KeyRound size={40} strokeWidth={1.1} aria-hidden="true" /></div><form className="form-stack cu-setting-body" onSubmit={changePassword}><Field label="Current password" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /><div className="form-grid"><Field label="New password" type="password" autoComplete="new-password" minLength={12} hint="At least 12 characters." value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required /><Field label="Confirm new password" type="password" autoComplete="new-password" minLength={12} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required /></div>{passwordMessage ? <Alert tone="danger">{passwordMessage}</Alert> : null}<Button type="submit" loading={busy === "password"}>Update password &amp; sign out <ArrowRight size={16} aria-hidden="true" /></Button></form></section>
        <section className="cu-devices-section" id="account-sessions"><header><div><span className="eyebrow">04 / Devices &amp; sessions</span><h2>Know who’s signed in.</h2><p>Review active browsers. Remove a device you don’t recognise.</p></div><Button variant="ghost" onClick={() => void reloadSessions()} disabled={sessionsLoading}><RefreshCw size={15} aria-hidden="true" />Refresh</Button></header>{sessionsLoading ? <LoadingBlock label="Loading your active devices" /> : sessionsError ? <PageError message={sessionsError} retry={() => void reloadSessions()} /> : sessions?.length ? <div className="cu-device-list">{sessions.map((session) => <article className="cu-device-row" key={session.id}><span className="cu-device-icon"><Laptop size={28} strokeWidth={1.3} aria-hidden="true" /></span><div><strong>{sessionName(session)}</strong><dl><div><dt>Last active</dt><dd>{formatDate(session.lastUsedAt ?? session.createdAt)}</dd></div><div><dt>Started</dt><dd>{formatDate(session.createdAt)}</dd></div>{session.ipAddress ? <div><dt>Network</dt><dd>{session.ipAddress}</dd></div> : null}</dl>{session.userAgent && session.userAgent !== sessionName(session) ? <details><summary>Browser details</summary><p>{session.userAgent}</p></details> : null}</div><Button variant="secondary" onClick={() => setSessionToRevoke(session)} disabled={busy === "session"}>Revoke</Button></article>)}</div> : <EmptyState title="No active devices" message="No active browser sessions were returned. Sign in again if you expected to see this device." />}{sessionToRevoke ? <div className="confirm-panel cu-revoke-confirmation"><div><strong>Remove this device?</strong><p>{sessionName(sessionToRevoke)} will need to sign in again.</p></div><div className="button-row"><Button variant="danger" loading={busy === "session"} onClick={() => void revokeSession()}>Revoke device</Button><Button variant="ghost" disabled={busy === "session"} onClick={() => setSessionToRevoke(null)}>Cancel</Button></div></div> : null}</section>
        <details className="cu-account-close"><summary><Trash2 size={18} strokeWidth={1.4} aria-hidden="true" /><span>Close your account</span><ChevronRight size={18} aria-hidden="true" /></summary><div><h2>Before you go.</h2><p>This permanently disables your Pepekart profile and revokes every browser session.</p><div className="cu-close-controls"><Field label='Type DELETE to confirm' value={deleteConfirmation} onChange={(event) => setDeleteConfirmation(event.target.value)} hint="This cannot be undone from this screen." /><Button variant="danger" loading={busy === "delete"} disabled={deleteConfirmation !== "DELETE"} onClick={() => void deleteAccount()}>Delete account</Button></div></div></details>
      </div>
    </div>
  </section>;
}
