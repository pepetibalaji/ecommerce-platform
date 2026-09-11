import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { Alert, Button, EmptyState, Field, LoadingBlock, PageError, Pagination, ProductImage, SelectField, StatusBadge, TextArea } from "../components/ui";
import { ApiError, type Inventory, type Order, type Payment, type Product, type Role, type User } from "../domain";
import { api } from "../lib/api";
import { createIdempotencyKey, formatDate, formatMoney, messageForError, toPage } from "../lib/format";
import { useResource } from "../lib/hooks";
import { ArrowRight, ArrowUpRight, Bell, Boxes, ChevronDown, ClipboardList, CreditCard, Info, Package, Plus, RefreshCw, Search, ShieldCheck, Users, X } from "lucide-react";
import { filterWorkspaceRecords } from "../lib/workspace-records";
import { useSellerProducts } from "../lib/useSellerProducts";

const productCategories = ["Apparel", "Home", "Accessories", "Stationery", "Electronics"];

type ProductDraft = {
  name: string;
  description: string;
  price: string;
  currency: string;
  category: string;
  brand: string;
  imageUrls: string;
  active: boolean;
};

const emptyProductDraft: ProductDraft = {
  name: "",
  description: "",
  price: "",
  currency: "INR",
  category: "",
  brand: "",
  imageUrls: "",
  active: true,
};

function productToDraft(product: Product): ProductDraft {
  return {
    name: product.name,
    description: product.description ?? "",
    price: String(product.price),
    currency: product.currency ?? "INR",
    category: product.category ?? "",
    brand: product.brand ?? "",
    imageUrls: (product.imageUrls ?? []).join("\n"),
    active: product.active !== false,
  };
}

type ProductWritePayload = Pick<Product, "name" | "price" | "currency"> & {
  description: string | null;
  category: string | null;
  brand: string | null;
  imageUrls: string[];
  active?: boolean;
};

function imageUrlsFromDraft(draft: ProductDraft) {
  return draft.imageUrls.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
}

function draftToProduct(draft: ProductDraft, includeActive = false): ProductWritePayload {
  const payload: ProductWritePayload = {
    name: draft.name.trim(),
    description: draft.description.trim() || null,
    price: Number(draft.price),
    currency: draft.currency,
    category: draft.category.trim() || null,
    brand: draft.brand.trim() || null,
    imageUrls: imageUrlsFromDraft(draft),
  };
  if (includeActive) payload.active = draft.active;
  return payload;
}

function parseBulkProducts(value: string): ProductWritePayload[] | null {
  try { const parsed: unknown = JSON.parse(value); return Array.isArray(parsed) ? parsed as ProductWritePayload[] : null; }
  catch { return null; }
}

function accessTokenOrThrow(token: string | null): string {
  if (!token) throw new Error("Your session has ended. Please sign in again.");
  return token;
}

function ContractNote({ children }: { children: ReactNode }) {
  return <details className="bo-contract-note"><summary><Info size={16} aria-hidden="true" />Workspace guidance & limitations<ChevronDown size={15} aria-hidden="true" /></summary><p>{children}</p></details>;
}

function MetricCard({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return <article className="metric-card"><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>;
}

function ProductFields({ draft, setDraft, showVisibility = false }: { draft: ProductDraft; setDraft: (next: ProductDraft) => void; showVisibility?: boolean }) {
  const firstImage = imageUrlsFromDraft(draft)[0];
  const preview: Product = { id: "draft-preview", name: draft.name || "Your product name", price: Number(draft.price) || 0, currency: draft.currency, imageUrls: firstImage?.startsWith("https://") ? [firstImage] : [] };
  return <div className="bo-product-fields">
    <div className="bo-field-groups">
      <fieldset className="bo-field-group"><legend>01 / Product essentials</legend><div className="form-grid">
        <Field className="form-span-full" label="Product name" placeholder="Give your product a clear, descriptive name" value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })} required />
        <SelectField label="Category" value={draft.category} onChange={event => setDraft({ ...draft, category: event.target.value })}><option value="">Uncategorised</option>{draft.category && !productCategories.includes(draft.category) ? <option value={draft.category}>{draft.category}</option> : null}{productCategories.map(category => <option key={category} value={category}>{category}</option>)}</SelectField>
        <Field label="Brand" placeholder="Brand or maker" value={draft.brand} onChange={event => setDraft({ ...draft, brand: event.target.value })} />
        <TextArea className="form-span-full" label="Description" placeholder="What should customers know about this product?" value={draft.description} onChange={event => setDraft({ ...draft, description: event.target.value })} rows={5} />
      </div></fieldset>
      <fieldset className="bo-field-group"><legend>02 / Pricing & visibility</legend><div className="form-grid">
        <Field label="Price" type="number" min="0.01" step="0.01" value={draft.price} onChange={event => setDraft({ ...draft, price: event.target.value })} required />
        <SelectField label="Currency" value={draft.currency} onChange={event => setDraft({ ...draft, currency: event.target.value })}><option value="INR">INR</option><option value="USD">USD</option><option value="EUR">EUR</option><option value="GBP">GBP</option></SelectField>
        {showVisibility ? <div className="form-span-full"><label className="check-field"><input type="checkbox" checked={draft.active} onChange={event => setDraft({ ...draft, active: event.target.checked })} /><span>Visible in the public catalogue</span></label><p className="muted">Enable visibility and save to reactivate a hidden or archived product.</p></div> : <p className="form-span-full muted">New products use the default visibility. You can update visibility after saving.</p>}
      </div></fieldset>
      <fieldset className="bo-field-group"><legend>03 / Product imagery</legend><TextArea label="Approved image URLs" hint="One approved HTTPS image URL per line, up to 10. The first image is your cover. Direct uploads are not available." value={draft.imageUrls} onChange={event => setDraft({ ...draft, imageUrls: event.target.value })} rows={4} /></fieldset>
    </div>
    <aside className="bo-product-preview" aria-label="Product preview"><span>LISTING PREVIEW</span><ProductImage product={preview} /><h3>{preview.name}</h3><p>{draft.category || "Choose a category"}{draft.brand ? " / " + draft.brand : ""}</p><strong>{Number(draft.price) > 0 ? formatMoney(Number(draft.price), draft.currency) : "Add a price"}</strong><p>This is a preview of your draft, not a published listing. Save the form to apply changes.</p></aside>
  </div>;
}

function RecordTools({ query, onQuery, count, total, loading, onReload, scope = "page" }: { query: string; onQuery: (value: string) => void; count: number; total: number; loading: boolean; onReload: () => void; scope?: "page" | "loaded" }) {
  return <div className="bo-record-tools">
    <label className="bo-record-search"><Search size={18} aria-hidden="true" /><span className="sr-only">{scope === "loaded" ? "Search loaded products" : "Search this page"}</span><input type="search" value={query} maxLength={200} placeholder={scope === "loaded" ? "Search loaded products…" : "Search this page…"} onChange={event => onQuery(event.target.value)} />{query ? <button type="button" aria-label="Clear search" onClick={() => onQuery("")}><X size={16} aria-hidden="true" /></button> : null}</label>
    <div className="bo-record-meta"><span role="status">{loading ? "Loading records…" : query ? count + (scope === "loaded" ? " matches among loaded products" : " matches on this page") : count + (scope === "loaded" ? " loaded / " : " on this page / ") + total + " total"}</span><Button variant="secondary" loading={loading} onClick={onReload}><RefreshCw size={14} aria-hidden="true" />Refresh</Button></div>
  </div>;
}

function productIsValid(draft: ProductDraft) {
  return !productValidation(draft);
}

function productValidation(draft: ProductDraft): string | null {
  if (!draft.name.trim()) return "Enter a product name.";
  if (!Number.isFinite(Number(draft.price)) || Number(draft.price) <= 0) return "Enter a positive price.";
  if (!/^[A-Z]{3}$/.test(draft.currency)) return "Choose an ISO currency.";
  const imageUrls = imageUrlsFromDraft(draft);
  if (imageUrls.length > 10) return "A product can have at most 10 image URLs.";
  if (imageUrls.some((url) => url.length > 2048)) return "Each image URL must be 2,048 characters or fewer.";
  if (imageUrls.some((url) => {
    try { return new URL(url).protocol !== "https:"; }
    catch { return true; }
  })) return "Each image URL must be a valid HTTPS URL.";
  return null;
}

function sellerOrderLineCount(order: Order) {
  return order.items.reduce((sum, item) => sum + item.quantity, 0);
}

function FulfilmentAddress({ order }: { order: Order }) {
  const address = order.shippingAddress;
  if (!address) return <span className="muted">Not supplied</span>;
  return <details className="fulfilment-address"><summary>View address</summary><address>{address.recipientName}<br />{address.line1}{address.line2 ? <><br />{address.line2}</> : null}<br />{address.city}, {address.state} {address.postalCode}<br />{address.country}{address.phone ? <><br />{address.phone}</> : null}</address></details>;
}

export function SellerOverviewPage() {
  const { accessToken } = useAuth();
  const { data, loading, error, reload } = useResource(async () => {
    const token = accessTokenOrThrow(accessToken);
    const [products, orders] = await Promise.all([
      api.products.sellerList(token, "page=0&size=100"),
      api.orders.sellerList(token, "page=0&size=100"),
    ]);
    return { products: toPage<Product>(products), orders: toPage<Order>(orders) };
  }, [accessToken]);
  const activeProducts = data?.products.content.filter((product) => product.active !== false).length ?? 0;
  const pendingOrders = data?.orders.content.filter((order) => order.status === "PENDING").length ?? 0;

  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">YOUR BUSINESS, ORGANISED</span><h1>Your shop, at a glance.</h1><p>A clear view of your products and orders, with your next steps close at hand.</p></div><Link className="button button-primary" to="/seller/products/new"><Plus size={16} aria-hidden="true" />Add product</Link></div>
    {loading ? <LoadingBlock label="Loading seller records" /> : error ? <PageError message={error} retry={() => void reload()} /> : <>
      <div className="metric-grid"><MetricCard label="Your products" value={data?.products.totalElements ?? 0} detail="Total products in your seller catalogue" /><MetricCard label="Order records" value={data?.orders.totalElements ?? 0} detail={pendingOrders + " pending in the loaded records"} /><MetricCard label="Visible listings" value={activeProducts} detail="Publicly visible in the loaded products" /></div>
      <p className="bo-snapshot-note">Visibility and pending counts reflect the first 100 loaded records, not platform-wide analytics.</p>
      <div className="bo-overview-columns">
        <section className="panel"><div className="bo-panel-heading"><h2>Your catalogue</h2><Link to="/seller/products">View all<ArrowRight size={15} aria-hidden="true" /></Link></div><div className="bo-recent-list">{data?.products.content.length ? data.products.content.slice(0, 5).map(product => <Link className="bo-recent-item" key={product.id} to={"/seller/products/" + product.id + "/edit"}><ProductImage compact product={product} /><div><strong>{product.name}</strong><span>{product.category || "Uncategorised"} / {product.active === false ? "Hidden" : "Visible"}</span></div><b>{formatMoney(product.price, product.currency)}</b></Link>) : <EmptyState title="Make your first listing" message="Your products will appear here after you create them." action={<Link className="button button-secondary" to="/seller/products/new">Add product</Link>} />}</div></section>
        <section className="panel"><div className="bo-panel-heading"><h2>Make your next move</h2></div><div className="bo-task-list"><Link to="/seller/products/new"><Plus size={22} aria-hidden="true" /><div><strong>Add something new</strong><span>Create a listing for your storefront.</span></div></Link><Link to="/seller/inventory"><Boxes size={22} aria-hidden="true" /><div><strong>Keep stock up to date</strong><span>Review and update a product's allocation.</span></div></Link><Link to="/seller/orders"><ClipboardList size={22} aria-hidden="true" /><div><strong>Review your orders</strong><span>See your order lines and fulfilment details.</span></div></Link></div></section>
      </div>
    </>}
    <ContractNote>Totals come from the seller product and order lists. This workspace does not provide revenue analytics, shipment actions, or payouts.</ContractNote>
  </section>;
}

export function SellerProductsPage() {
  const { accessToken, hasAnyRole } = useAuth();
  const [pageSearch, setPageSearch] = useState("");
  const [deleting, setDeleting] = useState<Product | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { products, totalElements, loading, loadingMore, error, loadMoreError, hasMore, loadMore, retry, reload, sentinelRef } = useSellerProducts(accessToken);
  const visibleRecords = filterWorkspaceRecords(products, pageSearch, record => [record.name, record.id, record.category, record.brand]);

  async function deleteProduct() {
    if (!deleting) return;
    setBusy(true); setActionError(null);
    try {
      await api.products.sellerArchive(accessTokenOrThrow(accessToken), deleting.id);
      setDeleting(null);
      await reload();
    } catch (caught) {
      setActionError(messageForError(caught));
    } finally { setBusy(false); }
  }

  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">Seller catalogue</span><h1>Products</h1><p>Manage your listings, keep details current, and jump straight to stock.</p></div><div className="action-row">{hasAnyRole("SELLER") ? <Link className="button button-secondary" to="/seller/products/import">Bulk import</Link> : null}<Link className="button button-primary" to="/seller/products/new">Add product</Link></div></div>
    <ContractNote>Products load in batches as you scroll. Search filters the products already loaded. Archived products remain here for your records and can be reactivated from the editor. Refreshing or archiving restarts the list to keep pagination accurate.</ContractNote>
    {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
    {deleting ? <Alert tone="warning" title={`Archive ${deleting.name}?`} action={<div className="action-row"><Button variant="danger" loading={busy} onClick={() => void deleteProduct()}>Confirm archive</Button><Button variant="secondary" disabled={busy} onClick={() => setDeleting(null)}>Keep product</Button></div>}>This hides the product from shoppers while preserving its history. You can reactivate it from the editor.</Alert> : null}
    <RecordTools query={pageSearch} onQuery={setPageSearch} count={visibleRecords.length} scope="loaded" total={totalElements} loading={loading} onReload={() => void reload()} />{loading ? <LoadingBlock label="Loading products" /> : error ? <PageError message={error} retry={() => void reload()} /> : products.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Product</th><th>Price</th><th>Visibility</th><th>Updated</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{!visibleRecords.length ? <tr><td colSpan={5}><div className="bo-table-empty"><span>No matches among loaded products. Scroll down to load more, or clear search.</span><Button variant="secondary" onClick={() => setPageSearch("")}>Clear search</Button></div></td></tr> : null}{visibleRecords.map((product) => <tr key={product.id}><td><div className="table-product"><ProductImage compact product={product} /><div><strong>{product.name}</strong><span>{product.category || "Uncategorised"}</span></div></div></td><td>{formatMoney(product.price, product.currency)}</td><td><StatusBadge value={product.active === false ? "INACTIVE" : "ACTIVE"} /></td><td>{formatDate(product.updatedAt)}</td><td><div className="action-row"><Link className="button button-ghost" to={`/seller/products/${product.id}/edit`}>Edit</Link><Link className="button button-ghost" to={`/seller/inventory?productId=${encodeURIComponent(product.id)}`}>Stock</Link><Button variant="danger" onClick={() => setDeleting(product)}>Archive</Button></div></td></tr>)}</tbody></table></div></> : <EmptyState title="No seller products yet" message="Create your first product to populate your storefront catalogue." action={<Link className="button button-primary" to="/seller/products/new">Add product</Link>} />}
    <div className="bo-product-loader" ref={sentinelRef}>
      {loadingMore ? <LoadingBlock label="Loading more products" /> : null}
      {loadMoreError ? <Alert tone="warning" title="Your loaded products are still here" action={<Button variant="secondary" onClick={retry}>Retry loading</Button>}>The next batch could not be loaded. Please try again.</Alert> : null}
      {!loading && !error && products.length > 0 ? <><span role="status">{products.length} of {totalElements} products loaded</span>{hasMore ? <><p>More products load automatically as you scroll.</p><Button variant="secondary" loading={loadingMore} onClick={loadMore}>Load more products</Button></> : <p>All products loaded.</p>}</> : null}
    </div>
  </section>;
}

export function SellerBulkImportPage() {
  const { accessToken } = useAuth();
  const [payload, setPayload] = useState("[\n  {\n    \"name\": \"Example product\",\n    \"description\": \"Short description\",\n    \"price\": 999,\n    \"currency\": \"INR\",\n    \"category\": \"Home\",\n    \"brand\": \"Your brand\",\n    \"imageUrls\": []\n  }\n]");
  const [busy, setBusy] = useState(false); const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const preview = parseBulkProducts(payload);
  const invalidRows = preview?.map((product, index) => ({ index, error: !product.name?.trim() ? "Missing name" : !Number.isFinite(Number(product.price)) || Number(product.price) <= 0 ? "Invalid price" : !/^[A-Z]{3}$/.test(product.currency ?? "") ? "Invalid currency" : null })).filter((row) => row.error) ?? [];
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!preview?.length || preview.length > 100 || invalidRows.length) { setFeedback({ tone: "danger", text: "Fix the previewed rows and keep the import between 1 and 100 products." }); return; }
    setBusy(true); setFeedback(null);
    try { const products = await api.products.sellerBulkCreate(accessTokenOrThrow(accessToken), preview); setFeedback({ tone: "success", text: `${products.length} products were added to your catalogue.` }); }
    catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Your catalogue</span><h1>Bulk import products</h1><p>Add up to 100 products to your own seller catalogue. Ownership is taken from your signed-in account.</p></div><Link className="button button-secondary" to="/seller/products">Back to products</Link></div><ContractNote>You cannot choose a seller here. Every accepted product is assigned to your authenticated seller account and is validated by the same content, price, currency, and image rules as a single product.</ContractNote><form className="panel form-stack" onSubmit={submit}><TextArea label="Products JSON" value={payload} onChange={(event) => setPayload(event.target.value)} rows={18} hint="Paste a JSON array. Review the preview before importing." />{preview ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Row</th><th>Name</th><th>Price</th><th>Currency</th><th>Status</th></tr></thead><tbody>{preview.slice(0, 10).map((product, index) => { const error = invalidRows.find((row) => row.index === index)?.error; return <tr key={index}><td>{index + 1}</td><td>{product.name || "—"}</td><td>{product.price ?? "—"}</td><td>{product.currency ?? "—"}</td><td>{error ? <span className="field-error">{error}</span> : <StatusBadge value="READY" />}</td></tr>; })}</tbody></table>{preview.length > 10 ? <p className="muted">Previewing the first 10 of {preview.length} products.</p> : null}</div> : <Alert tone="warning">Paste valid JSON to see a preview.</Alert>}{invalidRows.length ? <Alert tone="warning">Fix {invalidRows.length} invalid row{invalidRows.length === 1 ? "" : "s"} before importing.</Alert> : null}{feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}<div className="action-row"><Button type="submit" loading={busy} disabled={!preview?.length || preview.length > 100 || Boolean(invalidRows.length)}>Import {preview?.length ?? 0} products</Button></div></form></section>;
}

export function SellerProductEditorPage({ mode }: { mode?: "create" | "edit" }) {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const creating = mode === "create" || !productId || productId === "new";
  const { data: existing, loading, error } = useResource(async () => {
    if (creating) return null;
    return api.products.sellerById(accessTokenOrThrow(accessToken), productId!);
  }, [accessToken, productId, creating]);
  const [draft, setDraft] = useState<ProductDraft>(emptyProductDraft);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);

  useEffect(() => { if (existing) setDraft(productToDraft(existing)); }, [existing]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const validationError = productValidation(draft);
    if (validationError) { setSaveError(validationError); return; }
    setSaving(true); setSaveError(null); setSaveSuccess(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      const saved = creating
        ? await api.products.sellerCreate(token, draftToProduct(draft))
        : await api.products.sellerUpdate(token, productId!, draftToProduct(draft, true));
      if (creating) navigate(`/seller/products/${saved.id}/edit`, { replace: true });
      else { setDraft(productToDraft(saved)); setSaveSuccess("Product changes saved."); }
    } catch (caught) { setSaveError(messageForError(caught)); }
    finally { setSaving(false); }
  }

  if (loading) return <LoadingBlock label="Loading product editor" />;
  if (error) return <section className="backoffice-page"><EmptyState title="Product editor unavailable" message={error} action={<Link className="button button-primary" to="/seller/products">Return to products</Link>} /></section>;
  return <section className="backoffice-page editor-page">
    <div className="page-heading"><div><span className="eyebrow">Seller catalogue</span><h1>{creating ? "Add product" : "Edit product"}</h1><p>Public visibility, price, and product content are reviewed by the API when you save.</p></div><Link className="button button-secondary" to="/seller/products">Back to products</Link></div>
    <ContractNote>{creating ? "Inventory is prepared after a product is saved. If stock is not ready immediately, refresh Inventory shortly." : "You can edit your active, hidden, and archived products here. Enable visibility and save to return a product to the public catalogue."}</ContractNote>
    <form className="panel form-stack" onSubmit={submit}><ProductFields draft={draft} setDraft={setDraft} showVisibility={!creating} />{saveError ? <Alert tone="danger">{saveError}</Alert> : null}{saveSuccess ? <Alert tone="success">{saveSuccess}</Alert> : null}<div className="action-row"><Button type="submit" loading={saving} disabled={!productIsValid(draft)}>{creating ? "Create product" : "Save changes"}</Button>{!creating ? <Link className="button button-secondary" to={`/seller/inventory?productId=${encodeURIComponent(productId!)}`}>Manage stock</Link> : null}</div></form>
  </section>;
}

type InventoryArea = "seller" | "admin";

function InventoryWorkspace({ area }: { area: InventoryArea }) {
  const { accessToken } = useAuth();
  const [params, setParams] = useSearchParams();
  const { productId: routeProductId } = useParams();
  const [productId, setProductId] = useState(routeProductId ?? params.get("productId") ?? "");
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [availableStock, setAvailableStock] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isSeller = area === "seller";

  async function findInventory(event?: FormEvent) {
    event?.preventDefault();
    if (!productId.trim()) { setError("Enter a product ID."); return; }
    setBusy(true); setError(null); setMessage(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      const result = isSeller
        ? await api.inventory.sellerGet(token, productId.trim())
        : await api.inventory.adminGet(token, productId.trim());
      setInventory(result); setAvailableStock(String(result.availableStock));
      setParams({ productId: productId.trim() }, { replace: true });
    } catch (caught) {
      setInventory(null);
      const text = messageForError(caught);
      setError(caught instanceof ApiError && caught.status === 404 && isSeller
        ? `${text} Inventory can be provisioned asynchronously after product creation; wait briefly and retry, or create the initial allocation below when appropriate.`
        : text);
    } finally { setBusy(false); }
  }

  async function save(mode: "create" | "update") {
    const stock = Number(availableStock);
    if (!productId.trim() || !Number.isInteger(stock) || stock < 0) { setError("Use a product ID and a whole, non-negative stock value."); return; }
    setBusy(true); setError(null); setMessage(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      const result = mode === "create"
        ? isSeller
          ? await api.inventory.sellerCreate(token, productId.trim(), stock)
          : await api.inventory.adminCreate(token, productId.trim(), stock)
        : isSeller
          ? await api.inventory.sellerUpsert(token, productId.trim(), stock)
          : await api.inventory.adminUpsert(token, productId.trim(), stock);
      setInventory(result); setAvailableStock(String(result.availableStock)); setMessage(mode === "create" ? "Initial inventory allocation created." : "Inventory updated.");
    } catch (caught) { setError(messageForError(caught)); }
    finally { setBusy(false); }
  }

  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">{isSeller ? "Seller stock" : "Platform stock"}</span><h1>Inventory</h1><p>Stock controls operate on one known product at a time.</p></div>{isSeller ? <Link className="button button-secondary" to="/seller/products">Find a product ID</Link> : null}</div>
    <ContractNote>Neither role has an inventory-list or product-search endpoint. This screen intentionally does not invent a stock directory. Enter a known product ID from an authorised workflow.</ContractNote>
    <form className="panel form-stack" onSubmit={(event) => void findInventory(event)}><Field label="Product ID" value={productId} onChange={(event) => setProductId(event.target.value)} placeholder="UUID or product identifier" required /><div className="action-row"><Button type="submit" variant="secondary" loading={busy}>Find inventory</Button></div>{inventory ? <div className="inventory-readout"><div><span>Available stock</span><strong>{inventory.availableStock}</strong></div><div><span>Reserved stock</span><strong>{inventory.reservedStock}</strong></div></div> : null}<Field label="Available stock" type="number" min="0" step="1" value={availableStock} onChange={(event) => setAvailableStock(event.target.value)} hint="Only whole, non-negative units are accepted." />{error ? <Alert tone="danger">{error}</Alert> : null}{message ? <Alert tone="success">{message}</Alert> : null}<div className="action-row"><Button type="button" loading={busy} disabled={!productId.trim() || !availableStock} onClick={() => void save(inventory ? "update" : "create")}>{inventory ? "Update stock" : "Create initial stock"}</Button></div></form>
  </section>;
}

export function SellerInventoryPage() { return <InventoryWorkspace area="seller" />; }

export function SellerOrdersPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSearch, setPageSearch] = useState("");
  const { data, loading, error, reload } = useResource(() => api.orders.sellerList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Order>), [accessToken, pageIndex]);
  const visibleRecords = filterWorkspaceRecords(data?.content ?? [], pageSearch, record => [record.id, record.status]);
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Seller orders</span><h1>Order queue</h1><p>Review your order lines and the fulfilment details shared with you.</p></div></div><ContractNote>This is a read-only seller queue. The address snapshot is sensitive fulfilment context: it is revealed only in this queue, never logged by the browser, and no shipment, cancellation, refund, payout, messaging, or order-detail controls are fabricated.</ContractNote><RecordTools query={pageSearch} onQuery={setPageSearch} count={visibleRecords.length} total={data?.totalElements ?? 0} loading={loading} onReload={() => void reload()} />{loading ? <LoadingBlock label="Loading seller order queue" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Order</th><th>Items</th><th>Seller total</th><th>Status</th><th>Fulfilment address</th><th>Placed</th></tr></thead><tbody>{!visibleRecords.length ? <tr><td colSpan={6}><div className="bo-table-empty"><span>No matches on this page.</span><Button variant="secondary" onClick={() => setPageSearch("")}>Clear search</Button></div></td></tr> : null}{visibleRecords.map((order) => { const sellerTotal = order.sellerTotalAmount ?? order.sellerSubtotal; return <tr key={order.id}><td><code>{order.id.slice(0, 8)}</code></td><td>{sellerOrderLineCount(order)}</td><td>{sellerTotal === undefined ? "Not supplied" : formatMoney(sellerTotal, order.currency)}</td><td><StatusBadge value={order.status} /></td><td><FulfilmentAddress order={order} /></td><td>{formatDate(order.createdAt)}</td></tr>; })}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No seller order records" message="New seller-scoped order lines will appear here when the backend publishes them." />}</section>;
}

export function AdminOverviewPage() {
  const areas = [
    ["/admin/users", "Users & access", "Review accounts, manage roles, and keep access under control.", Users],
    ["/admin/catalogue", "Product catalogue", "Create listings for sellers or update a known product.", Package],
    ["/admin/inventory", "Inventory", "Look up a product and manage its available stock.", Boxes],
    ["/admin/orders", "Order management", "Review orders and apply supported status changes.", ClipboardList],
    ["/admin/payments", "Payments & refunds", "Inspect payment records before requesting a refund.", CreditCard],
    ["/admin/notifications", "Delivery recovery", "Review redacted delivery failures and recovery options.", Bell],
  ] as const;
  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">PLATFORM OPERATIONS</span><h1>Your control centre.</h1><p>The right tools, a clear overview, and a place for every operation.</p></div><Link className="button button-primary" to="/admin/catalogue/new"><Plus size={16} aria-hidden="true" />Create product</Link></div>
    <div className="bo-admin-intro"><div><h2>Keep the everyday running smoothly.</h2><p>Move from accounts to orders, catalogue to payments. Choose a workspace below to review records and manage the details that matter.</p></div><ShieldCheck aria-hidden="true" /></div>
    <div className="workspace-grid">{areas.map(([to, title, description, Icon]) => <article className="panel link-panel" key={to}><Link to={to}><div className="bo-module-icon"><Icon size={22} strokeWidth={1.6} aria-hidden="true" /></div><h2>{title}</h2><span>{description}</span><strong>Open workspace<ArrowRight size={15} aria-hidden="true" /></strong></Link></article>)}</div>
    <ContractNote>This overview links to operational tools. Aggregate platform analytics are not available, so no revenue, growth, or live-health metrics are shown.</ContractNote>
  </section>;
}

export function AdminUsersPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSearch, setPageSearch] = useState("");
  const { data, loading, error, reload } = useResource(() => api.admin.users(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<User>), [accessToken, pageIndex]);
  const visibleRecords = filterWorkspaceRecords(data?.content ?? [], pageSearch, record => [record.name, record.email, record.id, record.status, record.roles, record.role]);
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Identity operations</span><h1>Users</h1><p>Manage account access, roles, and status from one place.</p></div></div><ContractNote>The available API is paginated but does not publish search or filter semantics. Use a known user ID for a detailed record; browser permissions never replace server authorization.</ContractNote><RecordTools query={pageSearch} onQuery={setPageSearch} count={visibleRecords.length} total={data?.totalElements ?? 0} loading={loading} onReload={() => void reload()} />{loading ? <LoadingBlock label="Loading users" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th><span className="sr-only">Open user</span></th></tr></thead><tbody>{!visibleRecords.length ? <tr><td colSpan={5}><div className="bo-table-empty"><span>No matches on this page.</span><Button variant="secondary" onClick={() => setPageSearch("")}>Clear search</Button></div></td></tr> : null}{visibleRecords.map((record) => <tr key={record.id}><td><strong>{record.name}</strong><br /><code>{record.id.slice(0, 8)}</code></td><td>{record.email}</td><td>{(record.roles?.length ? record.roles : [record.role]).join(", ")}</td><td><StatusBadge value={record.status} /></td><td><Link className="button button-ghost" to={`/admin/users/${encodeURIComponent(record.id)}`}>Open</Link></td></tr>)}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No users returned" message="The API did not return a user record for this page." />}</section>;
}

const manageableRoles: Role[] = ["CUSTOMER", "SELLER", "ADMIN"];

function statusChoicesFor(current: User["status"]): User["status"][] {
  if (current === "ACTIVE") return ["ACTIVE", "SUSPENDED"];
  if (current === "SUSPENDED") return ["SUSPENDED", "ACTIVE"];
  return [];
}

export function AdminUserDetailPage() {
  const { userId = "" } = useParams();
  const { accessToken, user: viewer, hasPermission } = useAuth();
  const navigate = useNavigate();
  const { data: record, loading, error, setData } = useResource(() => api.admin.user(accessTokenOrThrow(accessToken), userId), [accessToken, userId]);
  const [status, setStatus] = useState<User["status"]>("ACTIVE");
  const [roles, setRoles] = useState<Role[]>([]);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [busyAction, setBusyAction] = useState<"status" | "roles" | "sessions" | "delete" | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmRoles, setConfirmRoles] = useState(false);
  const isSelf = record?.id === viewer?.id;
  const permissionClaimsAvailable = viewer?.permissions !== undefined;
  const allowed = (permission: string) => {
    // The current delete endpoint is intentionally authorized by USER:STATUS_WRITE.
    const effectivePermission = permission === "USER:DELETE" ? "USER:STATUS_WRITE" : permission;
    return !permissionClaimsAvailable || hasPermission(effectivePermission);
  };

  useEffect(() => {
    if (!record) return;
    setStatus(record.status);
    setRoles(record.roles?.length ? record.roles : [record.role]);
  }, [record]);

  async function perform(action: "status" | "roles" | "sessions" | "delete") {
    if (!record) return;
    if (action === "status" && !statusChoicesFor(record.status).includes(status)) {
      setFeedback({ tone: "warning", text: "That status transition is not supported by the current backend." });
      return;
    }
    if (action === "roles" && !confirmRoles) {
      setConfirmRoles(true);
      setFeedback({ tone: "warning", text: "Review the selected complete role set, then select Save roles again to confirm. Changing roles can revoke the user's active sessions." });
      return;
    }
    setBusyAction(action); setFeedback(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      if (action === "status") {
        await api.admin.userStatus(token, record.id, status);
        const updated = await api.admin.user(token, record.id);
        setData(updated); setFeedback({ tone: "success", text: "User status updated." });
      }
      if (action === "roles") {
        if (!roles.length) throw new Error("Select at least one role.");
        const updated = await api.admin.userRoles(token, record.id, roles);
        setData(updated); setConfirmRoles(false); setFeedback({ tone: "success", text: "User roles updated." });
      }
      if (action === "sessions") {
        await api.admin.revokeUserSessions(token, record.id);
        setFeedback({ tone: "success", text: "All active sessions were revoked." });
      }
      if (action === "delete") {
        await api.admin.deleteUser(token, record.id);
        navigate("/admin/users", { replace: true });
      }
    } catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusyAction(null); }
  }

  function toggleRole(role: Role) { setConfirmRoles(false); setRoles((current) => current.includes(role) ? current.filter((entry) => entry !== role) : [...current, role]); }
  if (loading) return <LoadingBlock label="Loading user record" />;
  if (error || !record) return <section className="backoffice-page"><EmptyState title="User record unavailable" message={error || "No user record was returned."} action={<Link className="button button-primary" to="/admin/users">Return to users</Link>} /></section>;
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Identity operations</span><h1>{record.name}</h1><p>{record.email} · <code>{record.id}</code></p></div><Link className="button button-secondary" to="/admin/users">Back to users</Link></div><ContractNote>{permissionClaimsAvailable ? "Actions without a matching claim are disabled. The backend remains the source of truth for every authorization decision." : "The current profile payload does not include permission claims, so controls remain visible and the backend will make the final authorization decision."}</ContractNote>{isSelf ? <Alert tone="warning">Self-service role, status, session revocation, and deletion controls are disabled to prevent accidental lockout.</Alert> : null}{feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}<div className="split-layout"><section className="panel form-stack"><h2>Account status</h2><StatusBadge value={record.status} /><SelectField label="New status" value={status} disabled={isSelf || !allowed("USER:STATUS_WRITE")} onChange={(event) => setStatus(event.target.value as User["status"])}><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="PENDING_VERIFICATION">Pending verification</option></SelectField><Button loading={busyAction === "status"} disabled={isSelf || !allowed("USER:STATUS_WRITE")} onClick={() => void perform("status")}>Save status</Button></section><section className="panel form-stack"><h2>Roles</h2>{manageableRoles.map((role) => <label className="check-field" key={role}><input type="checkbox" checked={roles.includes(role)} disabled={isSelf || !allowed("USER:ROLE_WRITE")} onChange={() => toggleRole(role)} /> <span>{role}</span></label>)}<Button loading={busyAction === "roles"} disabled={isSelf || !allowed("USER:ROLE_WRITE") || !roles.length} onClick={() => void perform("roles")}>Save roles</Button></section><section className="panel form-stack"><h2>Sessions and deletion</h2><p>Revoking sessions signs the person out on all devices. Deletion is a separate, destructive backend operation.</p><Button variant="secondary" loading={busyAction === "sessions"} disabled={isSelf || !allowed("USER:SESSION_REVOKE")} onClick={() => void perform("sessions")}>Revoke all sessions</Button>{confirmDelete ? <Alert tone="warning" title="Delete this user?" action={<div className="action-row"><Button variant="danger" loading={busyAction === "delete"} onClick={() => void perform("delete")}>Confirm delete</Button><Button variant="secondary" disabled={busyAction === "delete"} onClick={() => setConfirmDelete(false)}>Cancel</Button></div>}>This cannot be undone from the frontend.</Alert> : <Button variant="danger" disabled={isSelf || !allowed("USER:DELETE")} onClick={() => setConfirmDelete(true)}>Delete user</Button>}</section></div></section>;
}

export function AdminCataloguePage({ mode: requestedMode }: { mode?: "create" | "edit" }) {
  const { accessToken } = useAuth();
  const { productId: routeProductId } = useParams();
  const [mode, setMode] = useState<"create" | "edit">(requestedMode ?? (routeProductId ? "edit" : "create"));
  const [sellerId, setSellerId] = useState("");
  const [productId, setProductId] = useState(routeProductId ?? "");
  const [loadedProductId, setLoadedProductId] = useState<string | null>(null);
  const loaded = loadedProductId === productId.trim();
  const [draft, setDraft] = useState<ProductDraft>(emptyProductDraft);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState(false);

  useEffect(() => {
    if (requestedMode) setMode(requestedMode);
    if (routeProductId) { setProductId(routeProductId); setMode("edit"); }
  }, [requestedMode, routeProductId]);

  async function loadProduct() {
    if (!productId.trim()) { setFeedback({ tone: "danger", text: "Enter a known product ID first." }); return; }
    setBusy(true); setFeedback(null); setLoadedProductId(null); setConfirmArchive(false);
    try {
      const product = await api.products.adminById(accessTokenOrThrow(accessToken), productId.trim());
      setDraft(productToDraft(product)); setProductId(product.id); setSellerId(product.sellerId ?? ""); setLoadedProductId(product.id);
      setFeedback({ tone: "success", text: product.active === false ? "Hidden product loaded. Enable visibility and save to reactivate it." : "Product loaded and ready to edit." });
    } catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    const validationError = productValidation(draft);
    if (validationError) { setFeedback({ tone: "danger", text: validationError }); return; }
    if (mode === "create" && !sellerId.trim()) { setFeedback({ tone: "danger", text: "A seller ID is required to create an admin-managed product." }); return; }
    if (mode === "edit" && !loaded) { setFeedback({ tone: "danger", text: "Load this product before saving changes." }); return; }
    setBusy(true); setFeedback(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      const saved = mode === "create"
        ? await api.products.adminCreate(token, sellerId.trim(), draftToProduct(draft))
        : await api.products.adminUpdate(token, productId.trim(), draftToProduct(draft, true));
      setProductId(saved.id); setLoadedProductId(saved.id); setMode("edit"); setDraft(productToDraft(saved)); setFeedback({ tone: "success", text: mode === "create" ? `Product created with ID ${saved.id}.` : "Product updated." });
    } catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  async function archiveProduct() {
    if (!loaded) return;
    setBusy(true); setFeedback(null);
    try { await api.products.adminArchive(accessTokenOrThrow(accessToken), productId.trim()); setDraft(current => ({ ...current, active: false })); setFeedback({ tone: "success", text: "Product archived. Its history is preserved and shoppers can no longer open it." }); setConfirmArchive(false); }
    catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">Catalogue operations</span><h1>Product studio</h1><p>Create a product for a known seller or manage active, hidden, and archived products.</p></div></div>
    <ContractNote>Bulk import belongs in the seller workspace, where ownership comes from the authenticated seller. Admins manage individual product operations and catalogue delivery recovery here.</ContractNote>
    <div className="toolbar"><Button variant={mode === "create" ? "primary" : "secondary"} disabled={busy} onClick={() => { setMode("create"); setDraft(emptyProductDraft); setProductId(""); setSellerId(""); setLoadedProductId(null); setFeedback(null); setConfirmArchive(false); }}>Create product</Button><Button variant={mode === "edit" ? "primary" : "secondary"} disabled={busy} onClick={() => { setMode("edit"); setFeedback(null); setConfirmArchive(false); }}>Edit known product</Button></div>
    <form className="panel form-stack" onSubmit={save}>
      {mode === "create" ? <Field label="Seller ID" value={sellerId} onChange={(event) => setSellerId(event.target.value)} hint="The admin create API requires the target seller ID." required /> : <div className="form-grid"><Field label="Product ID" value={productId} disabled={busy} onChange={(event) => { setProductId(event.target.value); setConfirmArchive(false); }} required /><div className="field"><span className="field-label">Management details</span><Button type="button" variant="secondary" loading={busy} onClick={() => void loadProduct()}>Load product</Button></div></div>}
      <ProductFields draft={draft} setDraft={setDraft} showVisibility={mode === "edit"} />
      {mode === "edit" && !loaded ? <p className="muted">Load the product to review its current details before saving or archiving.</p> : null}
      {feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}
      <div className="action-row"><Button type="submit" loading={busy} disabled={!productIsValid(draft) || (mode === "edit" && !loaded)}>{mode === "create" ? "Create product" : "Save product"}</Button>{mode === "edit" ? (confirmArchive ? <Alert tone="warning" title="Archive this product?" action={<div className="action-row"><Button type="button" variant="danger" loading={busy} onClick={() => void archiveProduct()}>Confirm archive</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirmArchive(false)}>Cancel</Button></div>}>This hides the product from shoppers while preserving its history. Enable visibility and save to reactivate it later.</Alert> : <Button type="button" variant="danger" disabled={!loaded || busy} onClick={() => setConfirmArchive(true)}>Archive product</Button>) : null}</div>
    </form>
    <ProductDeliveryRecovery />
  </section>;
}

function ProductDeliveryRecovery() {
  const { accessToken } = useAuth();
  const [confirmation, setConfirmation] = useState<"replay" | "reconcile" | null>(null);
  const [busy, setBusy] = useState(false);
  const [nextAfterId, setNextAfterId] = useState<string | null>(null);
  const [started, setStarted] = useState(false);
  const [enqueued, setEnqueued] = useState(0);
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);

  async function recover() {
    if (!confirmation || busy) return;
    setBusy(true); setMessage(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      if (confirmation === "replay") {
        await api.products.replayDeadLetters(token);
        setMessage({ tone: "success", text: "Failed product deliveries have been returned to the queue." });
      } else {
        const result = await api.products.reconcile(token, nextAfterId ?? undefined);
        setEnqueued((nextAfterId ? enqueued : 0) + result.enqueued);
        setNextAfterId(result.nextAfterId); setStarted(true);
        setMessage({ tone: "success", text: result.enqueued + " product snapshots queued. " + (result.nextAfterId ? "Continue with the next batch." : "All batches are queued; connected services will process them shortly.") });
      }
      setConfirmation(null);
    } catch (caught) { setMessage({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  return <section className="panel form-stack" aria-labelledby="product-delivery-heading">
    <div><span className="eyebrow">Platform operations</span><h2 id="product-delivery-heading">Catalogue synchronization</h2><p>Restore product updates used by Inventory and other connected services after a delivery issue has been resolved.</p></div>
    <p>Reconciliation queues up to 100 current product snapshots at a time. It preserves stock quantities and product history.</p>
    {started ? <p role="status">{enqueued} snapshots queued in this run. {nextAfterId ? "More products remain." : "All batches queued."}</p> : null}
    {nextAfterId ? <details><summary>Next batch cursor</summary><code>{nextAfterId}</code></details> : null}
    {message ? <Alert tone={message.tone}>{message.text}</Alert> : null}
    {confirmation ? <Alert tone="warning" title={confirmation === "replay" ? "Replay failed Product deliveries?" : "Queue a catalogue reconciliation batch?"} action={<div className="action-row"><Button type="button" loading={busy} onClick={() => void recover()}>{confirmation === "replay" ? "Confirm replay" : "Queue batch"}</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirmation(null)}>Cancel</Button></div>}>{confirmation === "replay" ? "Every terminal Product delivery will be retried. Resolve the original delivery issue before continuing." : "This publishes current snapshots for the next 100 products. Existing snapshots may be received again and are handled safely."}</Alert> : <div className="action-row"><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirmation("replay")}>Replay Product dead letters</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirmation("reconcile")}>{nextAfterId ? "Reconcile next batch" : started ? "Start new reconciliation" : "Reconcile catalogue"}</Button></div>}
  </section>;
}

export function AdminInventoryPage() { return <InventoryWorkspace area="admin" />; }

function allowedTransitions(order: Order): Array<"CONFIRMED" | "CANCELLED"> {
  if (order.status === "PENDING") return ["CONFIRMED", "CANCELLED"];
  if (order.status === "CONFIRMED") return ["CANCELLED"];
  return [];
}

export function AdminOrdersPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSearch, setPageSearch] = useState("");
  const [targets, setTargets] = useState<Record<string, "CONFIRMED" | "CANCELLED">>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { data, loading, error, reload } = useResource(() => api.orders.adminList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Order>), [accessToken, pageIndex]);
  const visibleRecords = filterWorkspaceRecords(data?.content ?? [], pageSearch, record => [record.id, record.status]);
  async function transition(order: Order) {
    const status = targets[order.id] ?? allowedTransitions(order)[0];
    if (!status) return;
    setBusyId(order.id); setActionError(null);
    try { await api.orders.adminUpdateStatus(accessTokenOrThrow(accessToken), order.id, status); await reload(); }
    catch (caught) { setActionError(messageForError(caught)); }
    finally { setBusyId(null); }
  }
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Order operations</span><h1>Orders</h1><p>Review incoming orders and update supported order statuses.</p></div></div><ContractNote>Admin order detail is not available. The API only permits PENDING → CONFIRMED or CANCELLED, and CONFIRMED → CANCELLED. Payment and fulfilment results remain backend-owned.</ContractNote>{actionError ? <Alert tone="danger">{actionError}</Alert> : null}<RecordTools query={pageSearch} onQuery={setPageSearch} count={visibleRecords.length} total={data?.totalElements ?? 0} loading={loading} onReload={() => void reload()} />{loading ? <LoadingBlock label="Loading orders" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Order</th><th>Total</th><th>Status</th><th>Placed</th><th>Transition</th></tr></thead><tbody>{!visibleRecords.length ? <tr><td colSpan={5}><div className="bo-table-empty"><span>No matches on this page.</span><Button variant="secondary" onClick={() => setPageSearch("")}>Clear search</Button></div></td></tr> : null}{visibleRecords.map((order) => { const transitions = allowedTransitions(order); const target = targets[order.id] ?? transitions[0]; return <tr key={order.id}><td><code>{order.id.slice(0, 8)}</code></td><td>{formatMoney(order.totalAmount, order.currency)}</td><td><StatusBadge value={order.status} /></td><td>{formatDate(order.createdAt)}</td><td>{transitions.length ? <div className="action-row"><select className="input compact-input" aria-label={`New status for ${order.id}`} value={target} onChange={(event) => setTargets({ ...targets, [order.id]: event.target.value as "CONFIRMED" | "CANCELLED" })}>{transitions.map((status) => <option key={status} value={status}>{status}</option>)}</select><Button loading={busyId === order.id} onClick={() => void transition(order)}>Apply</Button></div> : <span className="muted">No UI transition</span>}</td></tr>; })}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No orders returned" message="There are no order records on this page." />}</section>;
}

export function AdminPaymentsPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSearch, setPageSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [refunding, setRefunding] = useState(false);
  const idempotencyKey = useRef<string | null>(null);
  const { data: page, loading, error, reload } = useResource(() => api.payments.adminList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Payment>), [accessToken, pageIndex]);
  const visibleRecords = filterWorkspaceRecords(page?.content ?? [], pageSearch, record => [record.id, record.orderId, record.status, record.provider]);
  const { data: detail, loading: detailLoading, error: detailError, reload: reloadDetail } = useResource(() => selectedId ? api.payments.adminById(accessTokenOrThrow(accessToken), selectedId) : Promise.resolve(null), [accessToken, selectedId]);

  useEffect(() => { if (detail) { setRefundAmount(String(detail.amount ?? "")); setRefundReason(""); idempotencyKey.current = null; setFeedback(null); } }, [detail]);
  async function requestRefund(event: FormEvent) {
    event.preventDefault();
    if (!detail) return;
    const amount = Number(refundAmount);
    if (!Number.isFinite(amount) || amount <= 0 || !detail.orderId || !detail.currency) { setFeedback({ tone: "danger", text: "This payment must include an order ID, currency, and a positive refund amount." }); return; }
    setRefunding(true); setFeedback(null);
    try {
      const key = idempotencyKey.current ?? createIdempotencyKey();
      idempotencyKey.current = key;
      const updated = await api.payments.refund(accessTokenOrThrow(accessToken), detail.id, { orderId: detail.orderId, amount, currency: detail.currency, reason: refundReason.trim() || undefined }, key);
      setFeedback({ tone: "success", text: `Refund request accepted with status ${updated.status}. Keep this page open until the provider result is reflected.` });
      await Promise.all([reload(), reloadDetail()]);
    } catch (caught) { setFeedback({ tone: "danger", text: `${messageForError(caught)} Retrying this same request reuses its idempotency key.` }); }
    finally { setRefunding(false); }
  }
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Payment operations</span><h1>Payments</h1><p>Inspect a payment before asking the backend to process a refund.</p></div></div><ContractNote>Only list, detail, and refund endpoints are available. A refund request is not an immediate refund result; provider callbacks and backend state are authoritative.</ContractNote><div className="split-layout payment-layout"><section><RecordTools query={pageSearch} onQuery={setPageSearch} count={visibleRecords.length} total={page?.totalElements ?? 0} loading={loading} onReload={() => void reload()} />{loading ? <LoadingBlock label="Loading payments" /> : error ? <PageError message={error} retry={() => void reload()} /> : page?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Payment</th><th>Order</th><th>Amount</th><th>Status</th><th></th></tr></thead><tbody>{!visibleRecords.length ? <tr><td colSpan={5}><div className="bo-table-empty"><span>No matches on this page.</span><Button variant="secondary" onClick={() => setPageSearch("")}>Clear search</Button></div></td></tr> : null}{visibleRecords.map((payment) => <tr key={payment.id}><td><code>{payment.id.slice(0, 8)}</code></td><td><code>{payment.orderId.slice(0, 8)}</code></td><td>{payment.amount === undefined ? "Not supplied" : formatMoney(payment.amount, payment.currency)}</td><td><StatusBadge value={payment.status} /></td><td><Button variant="ghost" onClick={() => setSelectedId(payment.id)}>Inspect</Button></td></tr>)}</tbody></table></div><Pagination page={page} onPage={setPageIndex} /></> : <EmptyState title="No payments returned" message="The API did not return a payment record for this page." />}</section><aside className="panel form-stack"><h2>Payment detail</h2>{!selectedId ? <p className="muted">Select a payment to request its server-backed detail.</p> : detailLoading ? <LoadingBlock label="Loading payment" /> : detailError || !detail ? <Alert tone="danger">{detailError || "Payment detail was unavailable."}</Alert> : <><dl className="detail-list"><div><dt>Payment ID</dt><dd><code>{detail.id}</code></dd></div><div><dt>Order ID</dt><dd><code>{detail.orderId}</code></dd></div><div><dt>Status</dt><dd><StatusBadge value={detail.status} /></dd></div><div><dt>Amount</dt><dd>{detail.amount === undefined ? "Not supplied" : formatMoney(detail.amount, detail.currency)}</dd></div><div><dt>Provider</dt><dd>{detail.provider || "Not supplied"}</dd></div></dl><form className="form-stack" onSubmit={requestRefund}><h3>Request refund</h3><Field label="Amount" type="number" min="0.01" step="0.01" value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} required /><Field label="Reason (optional)" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} maxLength={200} />{feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}<Button type="submit" variant="danger" loading={refunding}>Request refund</Button></form></>}</aside></div></section>;
}

export function AdminNotificationsPage() {
  const { accessToken } = useAuth();
  const { data, loading, error, reload } = useResource(() => api.admin.failedNotifications(accessTokenOrThrow(accessToken)), [accessToken]);
  const [replayConfirmation, setReplayConfirmation] = useState(false);
  const [replaying, setReplaying] = useState(false);
  const [replayMessage, setReplayMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);

  async function replayAuthDeadLetters() {
    setReplaying(true);
    setReplayMessage(null);
    try {
      const result = await api.admin.replayAuthOutboxDeadLetters(accessTokenOrThrow(accessToken));
      setReplayConfirmation(false);
      setReplayMessage({ tone: "success", text: result.replayed ? `${result.replayed} Auth outbox event${result.replayed === 1 ? "" : "s"} returned to the delivery queue.` : "There were no Auth outbox dead letters to replay." });
    } catch (caught) {
      setReplayMessage({ tone: "danger", text: messageForError(caught) });
    } finally {
      setReplaying(false);
    }
  }
  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">Operational diagnostics</span><h1>Failed notifications</h1><p>Review delivery issues and safely recover failed Auth messages.</p></div></div>
    <ContractNote>The notification endpoint can contain operational or personal data. This client intentionally redacts recipient and message fields.</ContractNote>
    <div className="split-layout">
      <section>
        {loading ? <LoadingBlock label="Loading failed notification diagnostics" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Diagnostic ID</th><th>Type</th><th>Status</th><th>Recorded</th><th>Content</th></tr></thead><tbody>{data.map((notification) => <tr key={notification.id}><td><code>{notification.id.slice(0, 8)}...</code></td><td>{notification.type || "Not supplied"}</td><td><StatusBadge value={notification.status || "FAILED"} /></td><td>{formatDate(notification.createdAt)}</td><td><span className="muted">Redacted</span></td></tr>)}</tbody></table></div> : <EmptyState title="No failed notifications" message="No failed-delivery records were returned by the endpoint." />}
      </section>
      <aside className="panel form-stack">
        <h2>Auth delivery recovery</h2>
        <p>Requeue terminal Auth outbox events only after the delivery issue has been resolved. This may retry verification, password-reset, or email-change delivery requests.</p>
        {replayMessage ? <Alert tone={replayMessage.tone}>{replayMessage.text}</Alert> : null}
        {replayConfirmation ? <Alert tone="warning" title="Replay Auth dead letters?" action={<div className="action-row"><Button variant="danger" loading={replaying} onClick={() => void replayAuthDeadLetters()}>Replay now</Button><Button variant="secondary" disabled={replaying} onClick={() => setReplayConfirmation(false)}>Cancel</Button></div>}>This requeues every terminal Auth outbox event. It does not expose token values or message content.</Alert> : <Button variant="secondary" onClick={() => setReplayConfirmation(true)}>Replay Auth dead letters</Button>}
      </aside>
    </div>
  </section>;
}
