import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { ArrowRight, ArrowUpRight, Search, ShieldCheck, ShoppingBag, SlidersHorizontal, Sparkles, Truck, X } from "lucide-react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import type { CartItem, FieldErrors, Order, Product, ShippingAddress } from "../domain";
import { ApiError } from "../domain";
import { api } from "../lib/api";
import { createIdempotencyKey, formatDate, formatMoney, messageForError, toPage } from "../lib/format";
import { useResource } from "../lib/hooks";
import { Alert, Button, EmptyState, Field, LoadingBlock, PageError, Pagination, ProductImage, SafeLink, SelectField, StatusBadge, TextArea } from "../components/ui";

const categories = ["Apparel", "Home", "Accessories", "Stationery", "Electronics"];
const blankAddress: ShippingAddress = { recipientName: "", phone: "", line1: "", line2: "", city: "", state: "", postalCode: "", country: "IN" };

function ProductCard({ product }: { product: Product }) {
  const { add } = useCart();
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  async function addToCart() {
    setAdding(true);
    setMessage(null);
    try { await add(product.id); setMessage("Added to cart."); }
    catch (error) { setMessage(messageForError(error)); }
    finally { setAdding(false); }
  }
  return <article className="product-card">
    <div className="product-card-visual"><Link to={`/products/${product.id}`} aria-label={`View ${product.name}`}><ProductImage product={product} /></Link><span className="product-category-pill">{product.category || "Featured"}</span></div>
    <div className="product-card-body"><div className="product-card-meta"><span>{product.brand || "Marketly"}</span><Link to={`/products/${product.id}`} aria-label={`Open ${product.name}`}><ArrowUpRight size={17} aria-hidden="true" /></Link></div><Link className="product-name" to={`/products/${product.id}`}>{product.name}</Link><div className="product-card-footer"><strong>{formatMoney(product.price, product.currency)}<small>Taxes calculated at checkout</small></strong><Button loading={adding} onClick={() => void addToCart()}><ShoppingBag size={16} aria-hidden="true" /><span>Add</span></Button></div>{message ? <span className="inline-note">{message}</span> : null}</div>
  </article>;
}

export function CataloguePage() {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const [draft, setDraft] = useState({ q: params.get("q") ?? "", category: params.get("category") ?? "", minPrice: params.get("minPrice") ?? "", maxPrice: params.get("maxPrice") ?? "", sort: params.get("sort") ?? "" });
  const { data, loading, error, reload } = useResource(() => api.products.list(query || "page=0&size=12"), [query]);
  const page = data ? toPage<Product>(data) : null;

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams();
    if (draft.q.trim()) next.set("q", draft.q.trim());
    if (draft.category) next.set("category", draft.category);
    if (draft.minPrice) next.set("minPrice", draft.minPrice);
    if (draft.maxPrice) next.set("maxPrice", draft.maxPrice);
    if (draft.sort) next.set("sort", draft.sort);
    next.set("page", "0");
    next.set("size", "12");
    setParams(next);
  }

  function clearFilters() {
    setDraft({ q: "", category: "", minPrice: "", maxPrice: "", sort: "" });
    setParams("page=0&size=12");
  }

  function selectCategory(category: string) {
    const nextCategory = draft.category === category ? "" : category;
    const nextDraft = { ...draft, category: nextCategory };
    setDraft(nextDraft);
    const next = new URLSearchParams(params);
    if (nextCategory) next.set("category", nextCategory); else next.delete("category");
    next.set("page", "0");
    next.set("size", "12");
    setParams(next);
  }

  const badPriceRange = Boolean(draft.minPrice && draft.maxPrice && Number(draft.minPrice) > Number(draft.maxPrice));
  const activeFilterCount = [params.get("q"), params.get("category"), params.get("minPrice"), params.get("maxPrice"), params.get("sort")].filter(Boolean).length;
  return <section className="catalogue-page">
    <div className="hero"><div className="hero-copy"><span className="eyebrow"><Sparkles size={14} aria-hidden="true" />The everyday edit</span><h1>Find pieces that make the everyday better.</h1><p>A considered marketplace for useful, beautiful finds. Final price and availability are always confirmed securely at checkout.</p><div className="hero-actions"><a className="button button-primary" href="#catalogue">Explore the collection<ArrowRight size={17} aria-hidden="true" /></a><span className="hero-caption">Fresh finds, simple checkout.</span></div><div className="hero-trust"><span><ShieldCheck size={17} aria-hidden="true" />Secure payment confirmation</span><span><Truck size={17} aria-hidden="true" />Address confirmed per order</span></div></div><div className="hero-showcase" aria-hidden="true"><div className="showcase-orbit" /><div className="showcase-card showcase-card-top"><Sparkles size={17} /><span>Curated<br />for today</span></div><div className="showcase-card showcase-card-bottom"><span className="showcase-price">01</span><span>Discover<br />something new</span></div></div><div className="hero-orb" aria-hidden="true" /></div>
    <section className="catalogue-discovery" id="catalogue" aria-labelledby="catalogue-title">
      <div className="catalogue-section-heading"><div><span className="eyebrow">Browse your way</span><h2 id="catalogue-title">The collection</h2></div><p>Search, compare, and add what you like. You stay in control until checkout.</p></div>
      <form className="catalogue-tools" onSubmit={applyFilters}>
        <div className="catalogue-tools-heading"><div className="tools-icon"><SlidersHorizontal size={19} aria-hidden="true" /></div><div><strong>Refine your search</strong><span>{activeFilterCount ? `${activeFilterCount} active filter${activeFilterCount === 1 ? "" : "s"}` : "Search by product, category, or price"}</span></div>{activeFilterCount ? <button className="clear-filter-button" type="button" onClick={clearFilters}><X size={15} aria-hidden="true" />Clear all</button> : null}</div>
        <label className="catalogue-search"><span className="sr-only">Search catalogue</span><Search size={20} aria-hidden="true" /><input value={draft.q} onChange={(event) => setDraft({ ...draft, q: event.target.value })} placeholder="Search products, brands, or ideas" /></label>
        <div className="catalogue-filter-grid"><SelectField label="Category" value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })}><option value="">All categories</option>{categories.map((category) => <option key={category}>{category}</option>)}</SelectField><Field label="Minimum price" type="number" min="0" value={draft.minPrice} onChange={(event) => setDraft({ ...draft, minPrice: event.target.value })} error={badPriceRange ? "Must be lower than maximum price." : undefined} placeholder="No minimum" /><Field label="Maximum price" type="number" min="0" value={draft.maxPrice} onChange={(event) => setDraft({ ...draft, maxPrice: event.target.value })} error={badPriceRange ? "Must be higher than minimum price." : undefined} placeholder="No maximum" /><SelectField label="Sort by" value={draft.sort} onChange={(event) => setDraft({ ...draft, sort: event.target.value })}><option value="">Recommended</option><option value="price,asc">Price: low to high</option><option value="price,desc">Price: high to low</option></SelectField></div>
        <div className="catalogue-chips" aria-label="Quick category filters"><span>Quick picks</span>{categories.map((category) => <button className={`filter-chip ${draft.category === category ? "active" : ""}`} key={category} type="button" onClick={() => selectCategory(category)}>{category}</button>)}</div>
        <div className="catalogue-actions"><Button type="submit" disabled={badPriceRange}><Search size={16} aria-hidden="true" />Show products</Button><Button type="button" variant="ghost" onClick={clearFilters}>Reset</Button></div>
      </form>
    </section>
    {error ? <PageError message={error} retry={() => void reload()} /> : null}
    {loading ? <ProductSkeletons /> : page?.content.length ? <><div className="catalogue-result-row"><div><span className="result-count">{page.totalElements}</span><span>products to explore</span></div><span className="result-hint"><Sparkles size={15} aria-hidden="true" />Prices are confirmed at checkout</span></div><div className="product-grid">{page.content.map((product) => <ProductCard key={product.id} product={product} />)}</div><Pagination page={page} onPage={(next) => { const copy = new URLSearchParams(params); copy.set("page", String(next)); copy.set("size", "12"); setParams(copy); }} /></> : <EmptyState title="No matching products" message="Try removing a filter or return to the complete catalogue." action={<Button onClick={clearFilters}>Clear filters</Button>} />}
  </section>;
}

function ProductSkeletons() { return <div className="product-grid">{Array.from({ length: 8 }).map((_, index) => <div className="product-card skeleton-card" key={index}><div className="skeleton skeleton-image" /><div className="product-card-body"><div className="skeleton skeleton-line" /><div className="skeleton skeleton-line short" /><div className="skeleton skeleton-button" /></div></div>)}</div>; }

export function ProductDetailPage() {
  const { productId = "" } = useParams();
  const { add } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const { data: product, loading, error, reload } = useResource(() => api.products.byId(productId), [productId]);
  async function addToCart() {
    if (!product) return;
    setAdding(true); setMessage(null);
    try { await add(product.id, quantity); setMessage("Added to cart. You can continue shopping or review your cart."); }
    catch (caught) { setMessage(messageForError(caught)); }
    finally { setAdding(false); }
  }
  if (loading) return <LoadingBlock label="Loading product" />;
  if (error || !product) return <EmptyState title="Product not found" message="This product may no longer be available in the public catalogue." action={<SafeLink to="/">Return to catalogue</SafeLink>} />;
  return <section className="product-detail"><div className="product-gallery"><ProductImage product={product} /><div className="gallery-note">Image fallback is shown if an approved product asset cannot load.</div></div><div className="product-information"><SafeLink to="/">← Back to catalogue</SafeLink><span className="eyebrow">{product.brand || "Marketplace"} · {product.category || "General"}</span><h1>{product.name}</h1><strong className="product-price">{formatMoney(product.price, product.currency)}</strong><p>{product.description || "More product details will be available soon."}</p><div className="quantity-control"><span>Quantity</span><Button variant="secondary" aria-label="Reduce quantity" onClick={() => setQuantity(Math.max(1, quantity - 1))}>−</Button><output>{quantity}</output><Button variant="secondary" aria-label="Increase quantity" onClick={() => setQuantity(Math.min(100, quantity + 1))}>+</Button></div><Button className="wide-button" loading={adding} onClick={() => void addToCart()}>Add {quantity} to cart</Button>{message ? <Alert tone={message.startsWith("Added") ? "success" : "danger"}>{message}</Alert> : null}<p className="muted">Price, product eligibility, and availability are verified again when you place an order.</p></div></section>;
}

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
  return <section className="cart-page"><div className="page-heading"><div><span className="eyebrow">{isAuthenticated ? "Signed-in cart" : "Guest cart"}</span><h1>Your cart</h1></div><SafeLink to="/">Continue shopping</SafeLink></div>{mergeState === "merging" ? <Alert title="Bringing your cart together">Your guest cart is being merged securely. Please do not refresh.</Alert> : null}{mergeState === "retry" ? <Alert tone="warning" title="Cart merge needs attention" action={<Button variant="secondary" onClick={() => void refresh()}>Refresh cart</Button>}>We kept your displayed items. Reload the authoritative cart before retrying.</Alert> : null}{error ? <PageError message={error} retry={() => void refresh()} /> : null}{cart?.items.length ? <div className="cart-layout"><div className="cart-lines">{cart.items.map((item) => <CartLine key={item.itemId} item={item} product={productMap.get(item.productId)} />)}</div><aside className="summary-card"><h2>Order summary</h2><div><span>Estimated items total</span><strong>{formatMoney(estimate)}</strong></div><p>Prices and availability are estimates here. The order service confirms the final result.</p>{isAuthenticated ? <Link className="button button-primary wide-button" to="/checkout">Proceed to checkout</Link> : <><Link className="button button-primary wide-button" to="/login" state={{ from: "/checkout" }}>Sign in to check out</Link><p className="muted">Your guest cart is retained by a secure browser cookie and merged after sign-in.</p></>}</aside></div> : <EmptyState title="Your cart is empty" message="Find something you like, then add it here." action={<Link className="button button-primary" to="/">Browse products</Link>} />}</section>;
}

export function LoginPage() {
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState<string | null>(null);
  const notice = (location.state as { notice?: string } | null)?.notice;
  async function submit(event: FormEvent) { event.preventDefault(); setError(null); try { const user = await login(email, password); const from = (location.state as { from?: string } | null)?.from; navigate(from || (user.role === "ADMIN" ? "/admin" : user.role === "SELLER" ? "/seller" : "/"), { replace: true }); } catch { setError("Invalid credentials. Check your email and password, then try again."); } }
  return <AuthShell title="Welcome back" subtitle="Sign in to continue securely."><form onSubmit={submit} className="form-stack"><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><Field label="Password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />{notice ? <Alert tone="success">{notice}</Alert> : null}{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={isLoading}>Sign in</Button><div className="form-links"><SafeLink to="/forgot-password">Forgot password?</SafeLink><span>New here? <SafeLink to="/register">Create account</SafeLink></span></div><p className="demo-hint">Demo mode: use an email containing <code>seller</code> or <code>admin</code> to view role workspaces.</p></form></AuthShell>;
}

export function RegisterPage() {
  const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [submitted, setSubmitted] = useState(false); const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); if (password.length < 12) { setError("Use at least 12 characters for your password."); return; } setLoading(true); setError(null); try { await api.auth.register(name, email, password); setSubmitted(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  if (submitted) return <AuthShell title="Check your inbox" subtitle="If this address is eligible, we have requested a verification email."><Alert title="Next step">Open the one-time link in the email. You will never need to copy a token into this site.</Alert><SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
  return <AuthShell title="Create your account" subtitle="Customer registration is ready for secure checkout."><form onSubmit={submit} className="form-stack"><Field label="Full name" autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} required /><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /><Field label="Password" type="password" autoComplete="new-password" minLength={12} hint="Use at least 12 characters." value={password} onChange={(event) => setPassword(event.target.value)} required />{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Create account</Button><p>Already have an account? <SafeLink to="/login">Sign in</SafeLink></p></form></AuthShell>;
}

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) { return <section className="auth-page"><div className="auth-card"><Link className="brand" to="/">marketly<span>.</span></Link><h1>{title}</h1><p>{subtitle}</p>{children}</div><aside className="auth-aside"><span className="eyebrow">Designed for safer commerce</span><h2>Clear, calm, and secure at every important step.</h2><p>We never display a raw verification or reset token, and account status is not exposed through recovery screens.</p></aside></section>; }

export function ForgotPasswordPage() {
  const [email, setEmail] = useState(""); const [sent, setSent] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent) { event.preventDefault(); setLoading(true); setError(null); try { await api.auth.forgotPassword(email); setSent(true); } catch (caught) { setError(messageForError(caught)); } finally { setLoading(false); } }
  return <AuthShell title="Reset your password" subtitle="We will send reset instructions when this address is eligible.">{sent ? <Alert title="Check your inbox">If eligible, a one-time reset link has been requested. It may take a few minutes to arrive.</Alert> : <form onSubmit={submit} className="form-stack"><Field label="Email address" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />{error ? <Alert tone="danger">{error}</Alert> : null}<Button type="submit" loading={loading}>Request reset link</Button></form>}<SafeLink to="/login">Return to sign in</SafeLink></AuthShell>;
}

function ResendVerificationForm() {
  const [email, setEmail] = useState("");
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
