import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import { ApiError, type Order, type Payment, type Product, type ShippingAddress } from "../domain";
import { api } from "../lib/api";
import { createIdempotencyKey, formatDate, formatMoney, messageForError, toPage } from "../lib/format";
import { useResource } from "../lib/hooks";
import { Alert, Button, EmptyState, Field, LoadingBlock, PageError, Pagination, ProductImage, SafeLink, SelectField, StatusBadge } from "../components/ui";

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

const paymentPollDelays = [700, 1_000, 1_600, 2_400, 3_600];

function wait(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));
}

function shortOrderId(orderId: string) {
  return orderId.length > 10 ? `#${orderId.slice(-8).toUpperCase()}` : `#${orderId}`;
}

function paymentIsTerminal(payment: Payment | null) {
  if (!payment) return false;
  return ["SUCCESS", "FAILED", "CANCELLED", "REFUNDED", "REFUND_FAILED", "PAYMENT_EXPIRED", "EXPIRED"].includes(payment.status);
}

function paymentMessage(payment: Payment | null, order: Order | null) {
  const status: string = payment?.status ?? order?.status ?? "PENDING";
  switch (status) {
    case "SUCCESS": return { tone: "success" as const, title: "Payment confirmed", text: "Your payment has been confirmed by the platform. Your order status may take a moment to update." };
    case "FAILED": return { tone: "danger" as const, title: "Payment was not completed", text: "The payment was not confirmed. Review your cart before starting a new checkout." };
    case "CANCELLED": return { tone: "warning" as const, title: "Payment was cancelled", text: "No completed payment is shown for this order. Review your cart before starting again." };
    case "PAYMENT_EXPIRED":
    case "EXPIRED": return { tone: "warning" as const, title: "Payment time expired", text: "This payment can no longer be used. Return to your cart and start a new checkout intent." };
    case "REFUND_REQUESTED":
    case "REFUND_PROCESSING": return { tone: "info" as const, title: "Refund is being processed", text: "The platform is processing the refund. Check this order later for the authoritative result." };
    case "REFUNDED": return { tone: "success" as const, title: "Refund completed", text: "The platform shows that this payment has been refunded." };
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

type CheckoutIntent = { key: string; fingerprint: string };

export function CheckoutPage() {
  const { accessToken } = useAuth();
  const { cart, isLoading: cartLoading, error: cartError, refresh: refreshCart } = useCart();
  const navigate = useNavigate();
  const [address, setAddress] = useState<ShippingAddress>(emptyAddress);
  const [submitting, setSubmitting] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [createdOrder, setCreatedOrder] = useState<Order | null>(null);
  const intent = useRef<CheckoutIntent | null>(null);
  const cartSignature = (cart?.items ?? []).map((item) => `${item.productId}:${item.quantity}`).sort().join("|");
  const { data: products, loading: productLoading } = useResource(
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
    intent.current = null;
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
      const safeError = messageForError(error);
      setCheckoutError(error instanceof ApiError && (error.status === 400 || error.status === 409)
        ? `${safeError} Your cart and address were kept unchanged. Review your cart, then submit a new checkout intent if you make changes.`
        : safeError);
      if (error instanceof ApiError && (error.status === 400 || error.status === 409)) void refreshCart();
    } finally {
      setSubmitting(false);
    }
  }

  if (cartLoading) return <LoadingBlock label="Loading checkout" />;
  if (createdOrder && accessToken) return <PaymentPreparation order={createdOrder} token={accessToken} onReviewOrder={() => navigate(`/orders/${createdOrder.id}`, { replace: true })} />;
  if (cartError) return <PageError message={cartError} retry={() => void refreshCart()} />;
  if (!cart?.items.length) return <EmptyState title="Your cart is empty" message="Add an item before beginning checkout." action={<Link className="button button-primary" to="/">Browse products</Link>} />;

  return <section className="checkout-page">
    <div className="page-heading"><div><span className="eyebrow">Secure checkout</span><h1>Where should we send your order?</h1><p>Enter an address for this order only. Addresses are not stored in this release.</p></div><SafeLink to="/cart">Return to cart</SafeLink></div>
    {mixedCurrencies ? <Alert tone="warning" title="Cart needs review">This cart contains items in more than one currency. Review the cart before checkout can continue.</Alert> : null}
    {checkoutError ? <Alert tone="danger" title="Checkout needs attention" action={<Button variant="secondary" onClick={() => void refreshCart()}>Refresh cart</Button>}>{checkoutError}</Alert> : null}
    <div className="checkout-layout">
      <form className="checkout-form form-stack" onSubmit={submit}>
        <h2>Shipping address</h2>
        <div className="form-grid"><Field label="Recipient name" autoComplete="shipping name" value={address.recipientName} onChange={(event) => setAddressValue("recipientName", event.target.value)} required /><Field label="Phone number" type="tel" autoComplete="shipping tel" value={address.phone} onChange={(event) => setAddressValue("phone", event.target.value)} required /></div>
        <Field label="Address line 1" autoComplete="shipping address-line1" value={address.line1} onChange={(event) => setAddressValue("line1", event.target.value)} required />
        <Field label="Address line 2 (optional)" autoComplete="shipping address-line2" value={address.line2 ?? ""} onChange={(event) => setAddressValue("line2", event.target.value)} />
        <div className="form-grid"><Field label="City" autoComplete="shipping address-level2" value={address.city} onChange={(event) => setAddressValue("city", event.target.value)} required /><Field label="State / region" autoComplete="shipping address-level1" value={address.state} onChange={(event) => setAddressValue("state", event.target.value)} required /></div>
        <div className="form-grid"><Field label="Postal code" autoComplete="shipping postal-code" value={address.postalCode} onChange={(event) => setAddressValue("postalCode", event.target.value)} required /><Field label="Country code" autoComplete="shipping country" maxLength={2} value={address.country} onChange={(event) => setAddressValue("country", event.target.value.toUpperCase())} required /></div>
        <Alert title="Final confirmation">When you place the order, the platform checks current price, eligibility, and availability. You are not charged on this page.</Alert>
        <Button type="submit" loading={submitting} disabled={mixedCurrencies || productLoading}>Place order and continue to payment</Button>
      </form>
      <aside className="summary-card checkout-summary"><h2>Order summary</h2>{productLoading ? <LoadingBlock label="Loading item details" /> : <div className="checkout-product-list">{cart.items.map((item) => { const product = productsById.get(item.productId); return product ? <div key={item.itemId} className="checkout-product-row"><ProductSummary product={product} /><span>× {item.quantity}</span></div> : <div key={item.itemId} className="checkout-product-row"><span>Product details unavailable</span><span>× {item.quantity}</span></div>; })}</div>}<div className="summary-total"><span>Estimated total</span><strong>{formatMoney(estimatedTotal, currency)}</strong></div><p>Final item prices and the total come from the order response after validation.</p></aside>
    </div>
  </section>;
}

function PaymentPreparation({ order, token, onReviewOrder }: { order: Order; token: string; onReviewOrder: () => void }) {
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [phase, setPhase] = useState<"preparing" | "ready" | "unavailable">("preparing");
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  const prepare = useCallback(async () => {
    setPhase("preparing");
    setError(null);
    for (let attempt = 0; attempt < paymentPollDelays.length; attempt += 1) {
      try {
        const nextPayment = await api.payments.byOrder(token, order.id);
        setPayment(nextPayment);
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
        if (caught instanceof ApiError && caught.status === 404 && attempt + 1 < paymentPollDelays.length) {
          await wait(paymentPollDelays[attempt]);
          continue;
        }
        setError(messageForError(caught));
        setPhase("unavailable");
        return;
      }
    }
    setError("Payment is still being prepared. Do not create another order; check this order again shortly.");
    setPhase("unavailable");
  }, [order.id, token]);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void prepare();
  }, [prepare]);

  function continueToProvider() {
    const checkoutUrl = payment?.checkoutUrl;
    if (!checkoutUrl) return;
    try {
      const parsed = new URL(checkoutUrl);
      if (parsed.protocol !== "https:" && parsed.protocol !== "http:") throw new Error("Unsupported URL protocol");
      window.location.assign(parsed.toString());
    } catch {
      setError("The secure payment link could not be opened. Check your order status and try again.");
      setPhase("unavailable");
    }
  }

  return <section className="payment-page payment-preparing"><span className="eyebrow">Order {shortOrderId(order.id)}</span><h1>{phase === "ready" ? "Your payment is ready" : "Preparing secure payment"}</h1>{phase === "preparing" ? <><LoadingBlock label="Confirming your order and preparing payment" /><p>Do not refresh or place another order while this is in progress.</p></> : null}{phase === "ready" ? <><Alert tone="info" title="Continue securely">You will leave Marketly for the payment provider. We verify the final payment result after you return.</Alert><Button onClick={continueToProvider}>Continue to secure payment</Button><Button variant="ghost" onClick={onReviewOrder}>Review order instead</Button></> : null}{phase === "unavailable" ? <><Alert tone="warning" title="Payment is not ready yet">{error}</Alert><div className="button-row"><Button variant="secondary" onClick={() => { started.current = true; void prepare(); }}>Check payment again</Button><Button variant="ghost" onClick={() => navigate(`/payment/return?orderId=${encodeURIComponent(order.id)}`)}>View payment status</Button><Button variant="ghost" onClick={onReviewOrder}>View order</Button></div></> : null}</section>;
}

type VerificationState = { order: Order | null; payment: Payment | null; loading: boolean; error: string | null; stopped: boolean; attempts: number };

export function PaymentReturnPage() {
  const { accessToken } = useAuth();
  const { refresh: refreshCart } = useCart();
  const [search] = useSearchParams();
  const orderId = search.get("orderId")?.trim() ?? "";
  const [state, setState] = useState<VerificationState>({ order: null, payment: null, loading: Boolean(orderId), error: null, stopped: false, attempts: 0 });

  const load = useCallback(async () => {
    if (!accessToken || !orderId) return null;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const order = await api.orders.byId(accessToken, orderId);
      let payment: Payment | null = null;
      try { payment = await api.payments.byOrder(accessToken, orderId); }
      catch (caught) { if (!(caught instanceof ApiError && caught.status === 404)) throw caught; }
      const result = { order, payment, terminal: paymentIsTerminal(payment) };
      setState((current) => ({ ...current, order, payment, loading: false, error: null }));
      return result;
    } catch (caught) {
      setState((current) => ({ ...current, loading: false, error: messageForError(caught) }));
      return { order: null, payment: null, terminal: !(caught instanceof ApiError && caught.retryable) };
    }
  }, [accessToken, orderId]);

  const needsNewCheckout = ["FAILED", "CANCELLED", "PAYMENT_EXPIRED", "EXPIRED"].includes(String(state.payment?.status ?? ""));

  useEffect(() => {
    if (needsNewCheckout) void refreshCart();
  }, [needsNewCheckout, refreshCart]);

  useEffect(() => {
    if (!orderId || !accessToken) return;
    let cancelled = false;
    async function poll() {
      for (let attempt = 0; attempt < paymentPollDelays.length; attempt += 1) {
        const result = await load();
        if (cancelled || !result || result.terminal) return;
        setState((current) => ({ ...current, attempts: attempt + 1 }));
        if (attempt + 1 < paymentPollDelays.length) await wait(paymentPollDelays[attempt]);
      }
      if (!cancelled) setState((current) => ({ ...current, stopped: true }));
    }
    void poll();
    return () => { cancelled = true; };
  }, [accessToken, load, orderId]);

  if (!orderId) return <EmptyState title="Payment status unavailable" message="Open this page from an order or from the approved payment return flow." action={<Link className="button button-primary" to="/orders">View orders</Link>} />;
  if (state.loading && !state.order) return <LoadingBlock label="Verifying your payment" />;
  if (state.error && !state.order) return <PageError message={state.error} retry={() => void load()} />;
  const status = paymentMessage(state.payment, state.order);
  return <section className="payment-page"><span className="eyebrow">Order {state.order ? shortOrderId(state.order.id) : ""}</span><h1>{status.title}</h1><Alert tone={status.tone} title={status.title}>{status.text}</Alert>{state.error ? <Alert tone="warning" title="Status update delayed">{state.error}</Alert> : null}{!paymentIsTerminal(state.payment) && !state.error ? <div className="payment-polling"><LoadingBlock label={state.stopped ? "Automatic checks paused" : "Checking the latest payment result"} />{state.stopped ? <p>We stopped automatic checks to avoid repeated requests. Refresh when you are ready.</p> : <p>Checking securely. Provider return details are never used as payment confirmation.</p>}</div> : null}<div className="order-status-grid"><div><span>Order status</span><StatusBadge value={state.order?.status} /></div><div><span>Payment status</span><StatusBadge value={state.payment?.status ?? "PREPARING"} /></div></div><div className="button-row"><Button variant="secondary" onClick={() => { setState((current) => ({ ...current, stopped: false })); void load(); }}>Refresh status</Button>{state.order ? <Link className="button button-primary" to={`/orders/${state.order.id}`}>View order</Link> : null}{needsNewCheckout ? <Link className="button button-ghost" to="/cart">Return to cart</Link> : <Link className="button button-ghost" to="/orders">Order history</Link>}</div></section>;
}

const orderStatusOptions = ["", "PENDING", "CONFIRMED", "PAYMENT_FAILED", "CANCELLED", "PARTIALLY_REFUNDED", "REFUNDED", "REFUND_REQUIRES_FULFILMENT_REVIEW"];

export function OrdersPage() {
  const { accessToken } = useAuth();
  const [params, setParams] = useSearchParams();
  const query = params.toString() || "page=0&size=10";
  const selectedStatus = params.get("status") ?? "";
  const { data, loading, error, reload } = useResource(() => api.orders.list(accessToken ?? "", query), [accessToken, query]);
  const page = data ? toPage<Order>(data) : null;
  function setStatus(status: string) { const next = new URLSearchParams(params); if (status) next.set("status", status); else next.delete("status"); next.set("page", "0"); next.set("size", "10"); setParams(next); }
  return <section className="orders-page"><div className="page-heading"><div><span className="eyebrow">Your purchases</span><h1>Orders</h1><p>Only the latest order and payment responses determine what is shown here.</p></div><SelectField label="Filter by status" value={selectedStatus} onChange={(event) => setStatus(event.target.value)}>{orderStatusOptions.map((value) => <option key={value} value={value}>{value ? value.replace(/_/g, " ") : "All statuses"}</option>)}</SelectField></div>{loading ? <LoadingBlock label="Loading your orders" /> : error ? <PageError message={error} retry={() => void reload()} /> : page?.content.length ? <><div className="order-list">{page.content.map((order) => <article className="order-card" key={order.id}><div><span className="eyebrow">{shortOrderId(order.id)} · {formatDate(order.createdAt)}</span><h2>{formatMoney(order.totalAmount, order.currency)}</h2><span>{order.items.length} item{order.items.length === 1 ? "" : "s"}</span></div><div className="order-card-actions"><StatusBadge value={order.status} /><Link className="button button-secondary" to={`/orders/${order.id}`}>View order</Link></div></article>)}</div><Pagination page={page} onPage={(nextPage) => { const next = new URLSearchParams(params); next.set("page", String(nextPage)); next.set("size", "10"); setParams(next); }} /></> : <EmptyState title={selectedStatus ? "No orders match this status" : "No orders yet"} message={selectedStatus ? "Try another order status or view all orders." : "When you complete checkout, your orders will appear here."} action={selectedStatus ? <Button onClick={() => setStatus("")}>Clear filter</Button> : <Link className="button button-primary" to="/">Browse products</Link>} />}</section>;
}

type OrderDetailData = { order: Order; payment: Payment | null };

export function OrderDetailPage() {
  const { accessToken } = useAuth();
  const { orderId = "" } = useParams();
  const [confirmCancellation, setConfirmCancellation] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
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
    try {
      const updated = await api.orders.cancel(accessToken, order.id);
      setData((current) => current ? { ...current, order: updated } : { order: updated, payment: null });
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
  return <section className="order-detail-page"><div className="page-heading"><div><SafeLink to="/orders">← Back to orders</SafeLink><span className="eyebrow">{shortOrderId(order.id)} · {formatDate(order.createdAt)}</span><h1>Order details</h1></div><StatusBadge value={order.status} /></div>{actionError ? <Alert tone="danger" title="Cancellation needs attention">{actionError}</Alert> : null}<div className="order-detail-layout"><div className="order-detail-main"><section className="detail-card"><h2>Items</h2><div className="order-item-list">{order.items.map((item, index) => <div key={item.id ?? `${item.productId}-${index}`} className="order-item"><div><strong>{item.productName ?? "Product"}</strong><span>Quantity {item.quantity}</span></div><div><span>{formatMoney(item.price, order.currency)} each</span><strong>{formatMoney(item.lineTotal ?? item.price * item.quantity, order.currency)}</strong></div></div>)}</div><div className="detail-total"><span>Order total</span><strong>{formatMoney(order.totalAmount, order.currency)}</strong></div></section><section className="detail-card"><h2>Payment</h2><StatusBadge value={data?.payment?.status ?? "PREPARING"} /><p>{paymentStatus.text}</p><Link className="link" to={`/payment/return?orderId=${encodeURIComponent(order.id)}`}>Check payment status</Link></section>{shipping ? <section className="detail-card"><h2>Shipping address</h2><address>{shipping.recipientName}<br />{shipping.line1}<br />{shipping.line2 ? <>{shipping.line2}<br /></> : null}{shipping.city}, {shipping.state} {shipping.postalCode}<br />{shipping.country}<br />{shipping.phone}</address><p className="muted">This is the address snapshot for this order.</p></section> : <section className="detail-card"><h2>Shipping address</h2><p>The address snapshot is not available in this order response.</p></section>}</div><aside className="summary-card"><h2>Order actions</h2>{order.cancelAllowed ? <>{confirmCancellation ? <><Alert tone="warning" title="Cancel this order?">This sends a cancellation request. The final outcome is shown only after the platform updates the order.</Alert><Button variant="danger" loading={cancelling} onClick={() => void cancelOrder()}>Yes, request cancellation</Button><Button variant="ghost" disabled={cancelling} onClick={() => setConfirmCancellation(false)}>Keep order</Button></> : <Button variant="secondary" onClick={() => setConfirmCancellation(true)}>Cancel order</Button>}</> : <p>This order is not currently eligible for cancellation.</p>}<Button variant="ghost" onClick={() => void reload()}>Refresh order</Button></aside></div></section>;
}

type SessionRecord = { id: string; createdAt?: string; expiresAt?: string; userAgent?: string };

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
  const [busy, setBusy] = useState<"profile" | "email" | "password" | "delete" | null>(null);
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
    try { await api.users.revokeSession(accessToken, sessionToRevoke.id); setSessionToRevoke(null); await reloadSessions(); }
    catch (caught) { setAccountMessage(messageForError(caught)); setSessionToRevoke(null); }
  }
  async function deleteAccount() {
    if (!accessToken || deleteConfirmation !== "DELETE") return;
    setBusy("delete"); setAccountMessage(null);
    try { await api.users.delete(accessToken); consumeSession(); navigate("/", { replace: true, state: { notice: "Your account was deleted." } }); }
    catch (caught) { setAccountMessage(messageForError(caught)); }
    finally { setBusy(null); }
  }

  return <section className="account-page"><div className="page-heading"><div><span className="eyebrow">Account settings</span><h1>Account and security</h1><p>Manage your profile and signed-in sessions without exposing security tokens.</p></div>{user ? <StatusBadge value={user.status} /> : null}</div>{accountMessage ? <Alert tone="danger" title="Account action needs attention">{accountMessage}</Alert> : null}<div className="account-grid"><section className="detail-card"><h2>Profile</h2><p className="muted">{user?.email}</p><form className="form-stack" onSubmit={updateProfile}><Field label="Full name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} required />{profileMessage ? <Alert tone={profileMessage.startsWith("Your profile") ? "success" : "danger"}>{profileMessage}</Alert> : null}<Button type="submit" loading={busy === "profile"}>Save profile</Button></form></section><section className="detail-card"><h2>Change email address</h2><p>We will request a confirmation link for the new address. The change only happens after the link is opened.</p><form className="form-stack" onSubmit={requestEmailChange}><Field label="New email address" type="email" autoComplete="email" value={newEmail} onChange={(event) => setNewEmail(event.target.value)} required />{emailMessage ? <Alert tone={emailMessage.startsWith("Check") ? "success" : "danger"}>{emailMessage}</Alert> : null}<Button type="submit" loading={busy === "email"}>Request confirmation link</Button></form></section><section className="detail-card"><h2>Change password</h2><p>A successful password change signs out all sessions, including this one.</p><form className="form-stack" onSubmit={changePassword}><Field label="Current password" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /><Field label="New password" type="password" autoComplete="new-password" minLength={12} hint="Use at least 12 characters." value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required /><Field label="Confirm new password" type="password" autoComplete="new-password" minLength={12} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required />{passwordMessage ? <Alert tone="danger">{passwordMessage}</Alert> : null}<Button type="submit" loading={busy === "password"}>Change password and sign out</Button></form></section><section className="detail-card"><div className="section-heading"><div><h2>Active sessions</h2><p>Revoke devices you no longer use.</p></div><Button variant="ghost" onClick={() => void reloadSessions()}>Refresh</Button></div>{sessionsLoading ? <LoadingBlock label="Loading sessions" /> : sessionsError ? <PageError message={sessionsError} retry={() => void reloadSessions()} /> : sessions?.length ? <div className="session-list">{sessions.map((session) => <div key={session.id} className="session-row"><div><strong>{session.userAgent || "Browser session"}</strong><span>Started {formatDate(session.createdAt)} · Expires {formatDate(session.expiresAt)}</span></div><Button variant="secondary" onClick={() => setSessionToRevoke(session)}>Revoke</Button></div>)}</div> : <EmptyState title="No active sessions found" message="Refresh this list if you expect a recent sign-in to appear." />}{sessionToRevoke ? <div className="confirm-panel"><p>Revoke this session? It will need to sign in again.</p><Button variant="danger" onClick={() => void revokeSession()}>Revoke session</Button><Button variant="ghost" onClick={() => setSessionToRevoke(null)}>Cancel</Button></div> : null}</section><section className="detail-card danger-zone"><h2>Delete account</h2><p>Deleting your account is destructive. It revokes refresh sessions and cannot be undone from this screen.</p><Field label='Type DELETE to confirm' value={deleteConfirmation} onChange={(event) => setDeleteConfirmation(event.target.value)} /><Button variant="danger" loading={busy === "delete"} disabled={deleteConfirmation !== "DELETE"} onClick={() => void deleteAccount()}>Delete account</Button></section></div></section>;
}
