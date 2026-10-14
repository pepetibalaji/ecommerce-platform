import { useEffect, useMemo, useRef, useState, type ComponentProps, type FormEvent, type ReactNode } from "react";
import { ArrowLeft, ArrowRight, Check, ChevronRight, KeyRound, LockKeyhole, Mail, Minus, Plus, ReceiptText, ShieldCheck, ShoppingBag, Sparkles, Trash2, UserRound, type LucideIcon } from "lucide-react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import type { CartItem, Product } from "../domain";
import { api, useMocks } from "../lib/api";
import { formatMoney, messageForError } from "../lib/format";
import { useResource } from "../lib/hooks";
import { Alert, Button, Field, LoadingBlock, PageError, ProductImage, SafeLink } from "../components/ui";
import "../styles/storefront-journey.css";

export { CataloguePage } from "./CatalogueExperience";
export { ProductDetailPage } from "./ProductDetailExperience";

function CartLine({ item, product, index }: { item: CartItem; product?: Product; index: number }) {
  const { update, remove } = useCart();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function change(nextQuantity: number) { setBusy(true); setError(null); try { await update(item.itemId, nextQuantity); } catch (caught) { setError(messageForError(caught)); } finally { setBusy(false); } }
  async function deleteItem() { setBusy(true); setError(null); try { await remove(item.itemId); } catch (caught) { setError(messageForError(caught)); } finally { setBusy(false); } }
  return <article className="cart-line">
    <span className="journey-item-index" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
    <Link className="journey-cart-image" to={`/products/${item.productId}`} aria-label={`View ${product?.name ?? "product"}`}><ProductImage product={product ?? { id: item.productId, name: "Product", price: 0 }} compact /></Link>
    <div className="cart-line-info"><span className="journey-item-category">{product?.category || "Pepekart collection"}</span><Link className="journey-item-name" to={`/products/${item.productId}`}><strong>{product?.name ?? "Product details are unavailable"}</strong></Link><span>{product ? `${formatMoney(product.price, product.currency)} each` : "Price confirmed at checkout"}</span><Button variant="ghost" className="journey-remove" aria-label={`Remove ${product?.name ?? "product"} from cart`} disabled={busy} onClick={() => void deleteItem()}><Trash2 size={13} aria-hidden="true" />Remove</Button>{error ? <span className="field-error" role="alert">{error}</span> : null}</div>
    <div className="journey-cart-quantity"><span className="journey-mobile-label">Quantity</span><div className="quantity-control"><Button variant="secondary" aria-label={`Reduce quantity of ${product?.name ?? "product"}`} disabled={busy || item.quantity <= 1} onClick={() => void change(item.quantity - 1)}><Minus size={15} aria-hidden="true" /></Button><output aria-label="Quantity" aria-live="polite">{item.quantity}</output><Button variant="secondary" aria-label={`Increase quantity of ${product?.name ?? "product"}`} disabled={busy || item.quantity >= cartMaxQuantity} onClick={() => void change(item.quantity + 1)}><Plus size={15} aria-hidden="true" /></Button></div></div>
    <div className="journey-line-total"><span className="journey-mobile-label">Amount</span><strong>{product ? formatMoney(product.price * item.quantity, product.currency) : "At checkout"}</strong></div>
  </article>;
}

export function CartPage() {
  const { cart, isLoading, error, mergeState, refresh, mergeGuest } = useCart();
  const { isAuthenticated } = useAuth();
  useEffect(() => { void refresh(); }, [refresh]);
  const productIds = cart?.items.map((item) => item.productId).join(",") ?? "";
  const { data: products } = useResource(() => Promise.all((cart?.items ?? []).map((item) => api.products.byId(item.productId).catch(() => null))).then((result) => result.filter((product): product is Product => Boolean(product))), [productIds]);
  const productMap = useMemo(() => new Map((products ?? []).map((product) => [product.id, product])), [products]);
  const estimate = (cart?.items ?? []).reduce((total, item) => total + (productMap.get(item.productId)?.price ?? 0) * item.quantity, 0);
  const itemCount = cart?.items.reduce((count, item) => count + item.quantity, 0) ?? 0;
  if (isLoading) return <LoadingBlock label="Loading your cart" />;
  return <section className="cart-page">
    <header className="journey-cart-heading"><div className="journey-bag-topline"><span className="journey-kicker">{isAuthenticated ? "Your personal edit" : "The guest edit"} / {String(itemCount).padStart(2, "0")} items</span><SafeLink to="/" className="journey-back-link"><ArrowLeft size={16} aria-hidden="true" />Continue shopping</SafeLink></div><div className="journey-bag-title"><h1>Your cart<span>.</span></h1><p>A good find is just the beginning.<br />Bring your everyday edit together.</p></div><ol className="journey-cart-steps" aria-label="Checkout steps"><li aria-current="step"><span>01</span>Your bag<ChevronRight size={13} aria-hidden="true" /></li><li><span>02</span>Checkout<ChevronRight size={13} aria-hidden="true" /></li><li><span>03</span>Payment</li></ol></header>
    {mergeState === "merging" ? <Alert title="Bringing your cart together">Your guest cart is being merged securely. Please do not refresh.</Alert> : null}
    {mergeState === "retry" ? <Alert tone="warning" title="Cart merge needs attention" action={<Button variant="secondary" onClick={() => void mergeGuest()}>Retry merge</Button>}>We could not finish bringing in your guest items. Retry the merge to check them again.</Alert> : null}
    {error ? <PageError message={error} retry={() => void refresh()} /> : null}
    {error && !cart ? null : cart?.items.length ? <div className="cart-layout">
      <section className="journey-bag-workspace" aria-labelledby="journey-selection-title"><header className="journey-workspace-heading"><h2 id="journey-selection-title">Your selection</h2><span>{cart.items.length} {cart.items.length === 1 ? "find" : "finds"}</span></header><div className="journey-table-head" aria-hidden="true"><span>Item</span><span>Quantity</span><span>Amount</span></div><div className="cart-lines">{cart.items.map((item, index) => <CartLine key={item.itemId} index={index} item={item} product={productMap.get(item.productId)} />)}</div><footer className="journey-bag-note"><ShieldCheck size={18} aria-hidden="true" /><p>Your selection, your pace. Make any changes here before checkout.</p></footer></section>
      <aside className="summary-card journey-summary"><div className="journey-receipt-header"><ReceiptText size={22} aria-hidden="true" /><span>Pepekart / Your bag</span><span>{String(itemCount).padStart(2, "0")}</span></div><span className="journey-kicker">The next chapter</span><h2 aria-label="Order summary">Order<br /><em>summary.</em></h2><dl className="journey-receipt-lines"><div><dt>Items in your bag</dt><dd>{itemCount}</dd></div><div><dt>Item estimate</dt><dd>{formatMoney(estimate)}</dd></div></dl><div className="journey-summary-total"><span>Estimated total</span><strong>{formatMoney(estimate)}</strong></div><p>Final prices and availability are confirmed at checkout.</p>{isAuthenticated ? <Link className="button button-primary wide-button" to="/checkout">Proceed to checkout<ArrowRight size={20} aria-hidden="true" /></Link> : <><Link className="button button-primary wide-button" to="/login" state={{ from: "/checkout" }}>Sign in to check out<ArrowRight size={20} aria-hidden="true" /></Link><p className="journey-guest-note">Your bag comes with you when you sign in.</p></>}<span className="journey-summary-security"><LockKeyhole size={14} aria-hidden="true" />Review your order before payment</span></aside>
    </div> : <div className="journey-empty-bag"><div className="journey-empty-copy"><span className="journey-kicker">A clean slate / a good start</span><h2>Your cart is empty</h2><p>Room for something<br /><em>you will love.</em></p><span>Discover the little things that make your everyday feel like you.</span><Link className="button button-primary" to="/">Browse products<ArrowRight size={19} aria-hidden="true" /></Link></div><div className="journey-empty-art" aria-hidden="true"><span className="journey-empty-index">BAG / 00</span><ShoppingBag size={132} strokeWidth={.75} /><span className="journey-empty-sticker"><Sparkles size={20} />Good things<br />come together.</span><span className="journey-empty-bottom">YOUR EVERYDAY, REIMAGINED</span></div></div>}
  </section>;
}

export function LoginPage() {
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState<string | null>(null);
  const notice = (location.state as { notice?: string } | null)?.notice;
  async function submit(event: FormEvent) { event.preventDefault(); setError(null); try { const user = await login(email, password); const from = (location.state as { from?: string } | null)?.from; navigate(from || (user.role === "ADMIN" ? "/admin" : user.role === "SELLER" ? "/seller" : "/"), { replace: true }); } catch { setError("Invalid credentials. Check your email and password, then try again."); } }
  return <AuthShell title="Welcome back" subtitle="Your bag, your orders, your little corner of Pepekart."><form onSubmit={submit} className="form-stack journey-access-form"><div className="journey-auth-inputs"><AuthField icon={Mail} label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><AuthField icon={KeyRound} label="Password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></div><div className="journey-login-recovery"><SafeLink to="/forgot-password">Forgot your password?</SafeLink></div>{notice ? <Alert tone="success">{notice}</Alert> : null}{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={isLoading}>Sign in<ArrowRight size={19} aria-hidden="true" /></Button><div className="journey-account-switch"><span>First time here?</span><SafeLink to="/register">Create account<ArrowRight size={15} aria-hidden="true" /></SafeLink></div>{useMocks ? <p className="demo-hint">Demo mode: use an email containing <code>seller</code> or <code>admin</code> to view role workspaces.</p> : null}</form></AuthShell>;
}

export function RegisterPage() {
  const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [submitted, setSubmitted] = useState(false); const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); if (password.length < 12) { setError("Use at least 12 characters for your password."); return; } setLoading(true); setError(null); try { await api.auth.register(name, email, password); setSubmitted(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  if (submitted) return <AuthShell title="Check your inbox" subtitle="If this address is eligible, we have requested a verification email."><Alert title="Next step">Open the one-time link in the email. You will never need to copy a token into this site.</Alert><ResendVerificationForm initialEmail={email} /><SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
  return <AuthShell title="Create your account" subtitle="A place to keep your bag together and follow your next find."><form onSubmit={submit} className="form-stack journey-access-form"><fieldset className="journey-auth-group"><legend><span>01</span>Your details</legend><AuthField icon={UserRound} label="Full name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} required /><AuthField icon={Mail} label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></fieldset><fieldset className="journey-auth-group"><legend><span>02</span>Secure your account</legend><AuthField icon={KeyRound} label="Password" type="password" autoComplete="new-password" minLength={12} hint="Use at least 12 characters." value={password} onChange={(event) => setPassword(event.target.value)} required /></fieldset>{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Create account<ArrowRight size={19} aria-hidden="true" /></Button><div className="journey-account-switch"><span>Already part of Pepekart?</span><SafeLink to="/login">Sign in<ArrowRight size={15} aria-hidden="true" /></SafeLink></div></form></AuthShell>;
}

function AuthField({ icon: Icon, ...props }: ComponentProps<typeof Field> & { icon: LucideIcon }) { return <div className="journey-auth-field"><Icon className="journey-auth-field-icon" size={18} aria-hidden="true" /><Field {...props} /></div>; }

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return <section className="auth-page journey-auth">
    <aside className="journey-auth-story"><div className="journey-story-top"><Link className="brand" to="/">pepekart<span>.</span></Link><span>Your everyday edit</span></div><div className="journey-story-heading"><span className="journey-kicker">A place for your good finds</span><h2>A little taste.<br /><em>A lot of possibility.</em></h2><p>The things you choose make your world.<br />Find a little more of you, here.</p></div><div className="journey-story-collage" aria-hidden="true"><div className="journey-story-picture journey-story-bag"><img src="https://cdn.dummyjson.com/product-images/womens-bags/heshe-women's-leather-bag/thumbnail.webp" alt="" decoding="async" onError={(event) => { event.currentTarget.style.display = "none"; }} /><span>Everyday carry / The edit</span></div><div className="journey-story-type"><Sparkles size={35} strokeWidth={1.3} /><span>Good<br /><em>things.</em></span></div><div className="journey-story-picture journey-story-home"><img src="https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp" alt="" decoding="async" onError={(event) => { event.currentTarget.style.display = "none"; }} /><span>Make yourself at home.</span></div><span className="journey-story-seal">A LITTLE<br />MORE YOU<Check size={17} /></span></div><div className="journey-story-footer"><span><span>01</span>Bring your bag together</span><span><span>02</span>Follow your orders</span><ArrowRight size={22} aria-hidden="true" /></div></aside>
    <div className="auth-card journey-auth-form-panel"><div className="journey-auth-topline"><Link to="/"><ArrowLeft size={15} aria-hidden="true" />Back to the store</Link><span>PEPEKART ACCOUNT</span></div><div className="journey-auth-form-inner"><header className="journey-auth-heading"><span className="journey-auth-emblem"><UserRound size={23} strokeWidth={1.5} aria-hidden="true" /></span><span className="journey-kicker">Make yourself at home</span><h1>{title}<span>.</span></h1><p>{subtitle}</p></header><div className="journey-auth-content">{children}</div><div className="journey-auth-security"><LockKeyhole size={14} aria-hidden="true" /><span>Your account, securely connected.</span></div></div><span className="journey-auth-footnote">Good finds. Yours to discover.</span></div>
  </section>;
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState(""); const [sent, setSent] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent) { event.preventDefault(); setLoading(true); setError(null); try { await api.auth.forgotPassword(email); setSent(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  return <AuthShell title="Reset your password" subtitle="We will send reset instructions when this address is eligible.">{sent ? <Alert title="Check your inbox">If eligible, a one-time reset link has been requested. It may take a few minutes to arrive.</Alert> : <form onSubmit={submit} className="form-stack"><AuthField icon={Mail} label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Request reset link</Button></form>}<SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
}

function ResendVerificationForm({ initialEmail = "" }: { initialEmail?: string }) {
  const [email, setEmail] = useState(initialEmail);
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api.auth.resendVerification(email);
      setSent(true);
    } catch (caught) {
      setError(messageForError(caught));
    } finally {
      setLoading(false);
    }
  }
  if (sent) return <Alert title="Check your inbox">If this address is eligible, a new one-time verification link has been requested.</Alert>;
  return <form className="form-stack" onSubmit={submit}>
    <AuthField icon={Mail} label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
    {error ? <Alert tone="danger">{error}</Alert> : null}
    <Button type="submit" loading={loading}>Request a new verification link</Button>
  </form>;
}

export function LinkActionPage({ action }: { action: "verify" | "reset" | "email-change" }) {
  const [search] = useSearchParams(); const token = search.get("token"); const [state, setState] = useState<"processing" | "success" | "invalid">("processing"); const [password, setPassword] = useState(""); const [loading, setLoading] = useState(false);
  // Strict Mode intentionally runs effects twice in development. These endpoints consume
  // one-time tokens, so an automatic confirmation must be sent at most once per link.
  const processedActionRef = useRef<string | null>(null);
  useEffect(() => {
    if (!token) { setState("invalid"); return; }
    if (action === "reset") return;

    const actionKey = `${action}:${token}`;
    if (processedActionRef.current === actionKey) return;
    processedActionRef.current = actionKey;

    const confirmation = action === "verify"
      ? api.auth.confirmVerification(token)
      : api.auth.confirmEmailChange(token);
    void confirmation.then(() => setState("success")).catch(() => setState("invalid"));
  }, [action, token]);
  async function reset(event: FormEvent) { event.preventDefault(); if (!token || password.length < 12) return; setLoading(true); try { await api.auth.resetPassword(token, password); setState("success"); } catch { setState("invalid"); } finally { setLoading(false); } }
  const labels = action === "verify" ? { processing: "Verifying your email", success: "Email verified", invalid: "This verification link cannot be used" } : action === "reset" ? { processing: "Choose a new password", success: "Password updated", invalid: "This reset link cannot be used" } : { processing: "Confirming your new email", success: "Email changed", invalid: "This email-change link cannot be used" };
  if (state === "processing" && action === "reset") return <AuthShell title={labels.processing} subtitle="This one-time link will be used when you save your new password."><form onSubmit={reset} className="form-stack"><AuthField icon={KeyRound} label="New password" type="password" minLength={12} hint="Use at least 12 characters." value={password} onChange={(event) => setPassword(event.target.value)} required /><Button type="submit" loading={loading}>Update password</Button></form></AuthShell>;
  if (state === "processing") return <AuthShell title={labels.processing} subtitle="This happens automatically. Do not copy or enter the token manually."><LoadingBlock label="Securely processing your one-time link" /></AuthShell>;
  if (state === "success") return <AuthShell title={labels.success} subtitle={action === "email-change" ? "For your security, all sessions have been signed out." : action === "reset" ? "All sessions have been signed out. Please sign in with your new password." : "You can now sign in to continue."}><Link className="button button-primary" to="/login">Sign in</Link></AuthShell>;
  return <AuthShell title={labels.invalid} subtitle="It may be expired, previously used, or no longer valid."><Alert tone="warning">For safety, we cannot identify why this link is unavailable.</Alert>{action === "verify" ? <ResendVerificationForm /> : <SafeLink to={action === "reset" ? "/forgot-password" : "/login"}>{action === "reset" ? "Request a new reset link" : "Sign in to start a new email change"}</SafeLink>}</AuthShell>;
}
const cartMaxQuantity = Number(import.meta.env.VITE_CART_MAX_QUANTITY_PER_ITEM ?? "100");
