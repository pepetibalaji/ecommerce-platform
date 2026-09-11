import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import type { CartItem, Product } from "../domain";
import { api, useMocks } from "../lib/api";
import { formatMoney, messageForError } from "../lib/format";
import { useResource } from "../lib/hooks";
import { Alert, Button, EmptyState, Field, LoadingBlock, PageError, ProductImage, SafeLink } from "../components/ui";

export { CataloguePage } from "./CatalogueExperience";
export { ProductDetailPage } from "./ProductDetailExperience";

function CartLine({ item, product }: { item: CartItem; product?: Product }) {
  const { update, remove } = useCart();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function change(nextQuantity: number) { setBusy(true); setError(null); try { await update(item.itemId, nextQuantity); } catch (caught) { setError(messageForError(caught)); } finally { setBusy(false); } }
  async function deleteItem() { setBusy(true); setError(null); try { await remove(item.itemId); } catch (caught) { setError(messageForError(caught)); } finally { setBusy(false); } }
  return <article className="cart-line"><ProductImage product={product ?? { id: item.productId, name: "Product", price: 0 }} compact /><div className="cart-line-info"><strong>{product?.name ?? "Product details are unavailable"}</strong><span>{product ? formatMoney(product.price, product.currency) : "Price confirmed at checkout"}</span>{error ? <span className="field-error">{error}</span> : null}</div><div className="cart-line-actions"><div className="quantity-control"><Button variant="secondary" disabled={busy || item.quantity <= 1} onClick={() => void change(item.quantity - 1)}>−</Button><output>{item.quantity}</output><Button variant="secondary" disabled={busy || item.quantity >= 100} onClick={() => void change(item.quantity + 1)}>+</Button></div><Button variant="ghost" disabled={busy} onClick={() => void deleteItem()}>Remove</Button></div></article>;
}

export function CartPage() {
  const { cart, isLoading, error, mergeState, refresh } = useCart();
  const { isAuthenticated } = useAuth();
  const productIds = cart?.items.map((item) => item.productId).join(",") ?? "";
  const { data: products } = useResource(() => Promise.all((cart?.items ?? []).map((item) => api.products.byId(item.productId).catch(() => null))).then((result) => result.filter((product): product is Product => Boolean(product))), [productIds]);
  const productMap = useMemo(() => new Map((products ?? []).map((product) => [product.id, product])), [products]);
  const estimate = (cart?.items ?? []).reduce((total, item) => total + (productMap.get(item.productId)?.price ?? 0) * item.quantity, 0);
  if (isLoading) return <LoadingBlock label="Loading your cart" />;
  return <section className="cart-page"><div className="page-heading"><div><span className="eyebrow">{isAuthenticated ? "Signed-in cart" : "Guest cart"}</span><h1>Your cart</h1></div><SafeLink to="/">Continue shopping</SafeLink></div>{mergeState === "merging" ? <Alert title="Bringing your cart together">Your guest cart is being merged securely. Please do not refresh.</Alert> : null}{mergeState === "retry" ? <Alert tone="warning" title="Cart merge needs attention" action={<Button variant="secondary" onClick={() => void refresh()}>Refresh cart</Button>}>We kept your displayed items. Reload the authoritative cart before retrying.</Alert> : null}{error ? <PageError message={error} retry={() => void refresh()} /> : null}{cart?.items.length ? <div className="cart-layout"><div className="cart-lines">{cart.items.map((item) => <CartLine key={item.itemId} item={item} product={productMap.get(item.productId)} />)}</div><aside className="summary-card"><h2>Order summary</h2><div><span>Estimated items total</span><strong>{formatMoney(estimate)}</strong></div><p>Final prices and availability are confirmed at checkout.</p>{isAuthenticated ? <Link className="button button-primary wide-button" to="/checkout">Proceed to checkout</Link> : <><Link className="button button-primary wide-button" to="/login" state={{ from: "/checkout" }}>Sign in to check out</Link><p className="muted">Your bag comes with you when you sign in.</p></>}</aside></div> : <EmptyState title="Your cart is empty" message="Find something you like, then add it here." action={<Link className="button button-primary" to="/">Browse products</Link>} />}</section>;
}

export function LoginPage() {
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState<string | null>(null);
  const notice = (location.state as { notice?: string } | null)?.notice;
  async function submit(event: FormEvent) { event.preventDefault(); setError(null); try { const user = await login(email, password); const from = (location.state as { from?: string } | null)?.from; navigate(from || (user.role === "ADMIN" ? "/admin" : user.role === "SELLER" ? "/seller" : "/"), { replace: true }); } catch { setError("Invalid credentials. Check your email and password, then try again."); } }
  return <AuthShell title="Welcome back" subtitle="Sign in to continue securely."><form onSubmit={submit} className="form-stack"><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><Field label="Password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />{notice ? <Alert tone="success">{notice}</Alert> : null}{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={isLoading}>Sign in</Button><div className="form-links"><SafeLink to="/forgot-password">Forgot password?</SafeLink><span>New here? <SafeLink to="/register">Create account</SafeLink></span></div>{useMocks ? <p className="demo-hint">Demo mode: use an email containing <code>seller</code> or <code>admin</code> to view role workspaces.</p> : null}</form></AuthShell>;
}

export function RegisterPage() {
  const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [submitted, setSubmitted] = useState(false); const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); if (password.length < 12) { setError("Use at least 12 characters for your password."); return; } setLoading(true); setError(null); try { await api.auth.register(name, email, password); setSubmitted(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  if (submitted) return <AuthShell title="Check your inbox" subtitle="If this address is eligible, we have requested a verification email."><Alert title="Next step">Open the one-time link in the email. You will never need to copy a token into this site.</Alert><ResendVerificationForm initialEmail={email} /><SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
  return <AuthShell title="Create your account" subtitle="Customer registration is ready for secure checkout."><form onSubmit={submit} className="form-stack"><Field label="Full name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} required /><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><Field label="Password" type="password" autoComplete="new-password" minLength={12} hint="Use at least 12 characters." value={password} onChange={(event) => setPassword(event.target.value)} required />{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Create account</Button><p>Already have an account? <SafeLink to="/login">Sign in</SafeLink></p></form></AuthShell>;
}

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) { return <section className="auth-page"><div className="auth-card"><Link className="brand" to="/">pepekart<span>.</span></Link><h1>{title}</h1><p>{subtitle}</p>{children}</div><aside className="auth-aside"><span className="eyebrow">Your everyday, a little better</span><h2>Good finds.<br />A place to keep them.</h2><p>Pick up where you left off, keep your bag together, and follow your orders. Make yourself at home.</p></aside></section>; }

export function ForgotPasswordPage() {
  const [email, setEmail] = useState(""); const [sent, setSent] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent) { event.preventDefault(); setLoading(true); setError(null); try { await api.auth.forgotPassword(email); setSent(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  return <AuthShell title="Reset your password" subtitle="We will send reset instructions when this address is eligible.">{sent ? <Alert title="Check your inbox">If eligible, a one-time reset link has been requested. It may take a few minutes to arrive.</Alert> : <form onSubmit={submit} className="form-stack"><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Request reset link</Button></form>}<SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
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
    <Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
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
  if (state === "processing" && action === "reset") return <AuthShell title={labels.processing} subtitle="This one-time link will be used when you save your new password."><form onSubmit={reset} className="form-stack"><Field label="New password" type="password" minLength={12} hint="Use at least 12 characters." value={password} onChange={(event) => setPassword(event.target.value)} required /><Button type="submit" loading={loading}>Update password</Button></form></AuthShell>;
  if (state === "processing") return <AuthShell title={labels.processing} subtitle="This happens automatically. Do not copy or enter the token manually."><LoadingBlock label="Securely processing your one-time link" /></AuthShell>;
  if (state === "success") return <AuthShell title={labels.success} subtitle={action === "email-change" ? "For your security, all sessions have been signed out." : action === "reset" ? "All sessions have been signed out. Please sign in with your new password." : "You can now sign in to continue."}><Link className="button button-primary" to="/login">Sign in</Link></AuthShell>;
  return <AuthShell title={labels.invalid} subtitle="It may be expired, previously used, or no longer valid."><Alert tone="warning">For safety, we cannot identify why this link is unavailable.</Alert>{action === "verify" ? <ResendVerificationForm /> : <SafeLink to={action === "reset" ? "/forgot-password" : "/login"}>{action === "reset" ? "Request a new reset link" : "Sign in to start a new email change"}</SafeLink>}</AuthShell>;
}
