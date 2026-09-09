import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { Alert, Button, EmptyState, Field, LoadingBlock, PageError, Pagination, ProductImage, SelectField, StatusBadge, TextArea } from "../components/ui";
import { ApiError, type Inventory, type Order, type Payment, type Product, type Role, type User } from "../domain";
import { api } from "../lib/api";
import { createIdempotencyKey, formatDate, formatMoney, messageForError, toPage } from "../lib/format";
import { useResource } from "../lib/hooks";

const productCategories = ["Apparel", "Home", "Accessories", "Stationery", "Electronics"];

type ProductDraft = {
  name: string;
  description: string;
  price: string;
  category: string;
  brand: string;
  imageUrls: string;
  active: boolean;
};

const emptyProductDraft: ProductDraft = {
  name: "",
  description: "",
  price: "",
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
    category: product.category ?? "",
    brand: product.brand ?? "",
    imageUrls: (product.imageUrls ?? []).join("\n"),
    active: product.active !== false,
  };
}

type ProductWritePayload = Pick<Product, "name" | "price"> & {
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
    category: draft.category.trim() || null,
    brand: draft.brand.trim() || null,
    imageUrls: imageUrlsFromDraft(draft),
  };
  if (includeActive) payload.active = draft.active;
  return payload;
}

function accessTokenOrThrow(token: string | null): string {
  if (!token) throw new Error("Your session has ended. Please sign in again.");
  return token;
}

function ContractNote({ children }: { children: ReactNode }) {
  return <Alert tone="warning" title="Backend contract boundary">{children}</Alert>;
}

function MetricCard({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return <article className="metric-card"><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>;
}

function ProductFields({ draft, setDraft, showVisibility = false }: { draft: ProductDraft; setDraft: (next: ProductDraft) => void; showVisibility?: boolean }) {
  return <div className="form-grid">
    <Field label="Product name" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} required />
    <Field label="Price" type="number" min="0.01" step="0.01" value={draft.price} onChange={(event) => setDraft({ ...draft, price: event.target.value })} required />
    <div className="field"><span className="field-label">Currency</span><div className="input" aria-readonly="true">INR (stage display default)</div></div>
    <SelectField label="Category" value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })}><option value="">Uncategorised</option>{productCategories.map((category) => <option key={category} value={category}>{category}</option>)}</SelectField>
    <Field label="Brand" value={draft.brand} onChange={(event) => setDraft({ ...draft, brand: event.target.value })} />
    <TextArea className="form-span-full" label="Description" value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} rows={4} />
    <TextArea className="form-span-full" label="Approved image URLs" hint="One HTTPS URL per line, up to 10. Media upload is not available in the current API." value={draft.imageUrls} onChange={(event) => setDraft({ ...draft, imageUrls: event.target.value })} rows={3} />
    {showVisibility ? <label className="check-field form-span-full"><input type="checkbox" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} /> <span>Visible in the public catalogue</span></label> : <p className="form-span-full muted">New products use the backend's default visibility. Visibility can be changed after creation.</p>}
  </div>;
}

function productIsValid(draft: ProductDraft) {
  return !productValidation(draft);
}

function productValidation(draft: ProductDraft): string | null {
  if (!draft.name.trim()) return "Enter a product name.";
  if (!Number.isFinite(Number(draft.price)) || Number(draft.price) <= 0) return "Enter a positive price.";
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
    <div className="page-heading"><div><span className="eyebrow">Seller area</span><h1>Your workspace</h1><p>Manage the catalogue you own and inspect the order lines shared with you.</p></div><Link className="button button-primary" to="/seller/products/new">Add product</Link></div>
    <ContractNote>There is no seller dashboard or aggregate analytics endpoint. These figures summarize the first 100 records returned to this browser and are not financial reporting.</ContractNote>
    {loading ? <LoadingBlock label="Loading seller records" /> : error ? <PageError message={error} retry={() => void reload()} /> : <>
      <div className="metric-grid">
        <MetricCard label="Products" value={data?.products.totalElements ?? 0} detail={`${activeProducts} visible in the loaded set`} />
        <MetricCard label="Order records" value={data?.orders.totalElements ?? 0} detail={`${pendingOrders} pending in the loaded set`} />
        <MetricCard label="Inventory" value="By product" detail="Use a known product ID to create or change stock." />
      </div>
      <div className="panel action-panel"><div><h2>What you can do today</h2><p>Product, inventory, and seller-order endpoints are available. Fulfilment actions, customer details, media uploads, filters, and server-side analytics are not yet published.</p></div><div className="action-row"><Link className="button button-secondary" to="/seller/products">Products</Link><Link className="button button-secondary" to="/seller/inventory">Inventory</Link><Link className="button button-secondary" to="/seller/orders">Order queue</Link></div></div>
    </>}
  </section>;
}

export function SellerProductsPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [deleting, setDeleting] = useState<Product | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { data, loading, error, reload } = useResource(() => api.products.sellerList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Product>), [accessToken, pageIndex]);

  async function deleteProduct() {
    if (!deleting) return;
    setBusy(true); setActionError(null);
    try {
      await api.products.sellerDelete(accessTokenOrThrow(accessToken), deleting.id);
      setDeleting(null);
      await reload();
    } catch (caught) {
      setActionError(messageForError(caught));
    } finally { setBusy(false); }
  }

  return <section className="backoffice-page">
    <div className="page-heading"><div><span className="eyebrow">Seller catalogue</span><h1>Products</h1><p>Only products owned by the signed-in seller are returned by this endpoint.</p></div><Link className="button button-primary" to="/seller/products/new">Add product</Link></div>
    <ContractNote>There is no server-side search, filter, bulk edit, media upload, or individual seller-product read endpoint. This list is the source for opening an edit form.</ContractNote>
    {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
    {deleting ? <Alert tone="warning" title={`Delete ${deleting.name}?`} action={<div className="action-row"><Button variant="danger" loading={busy} onClick={() => void deleteProduct()}>Delete</Button><Button variant="secondary" disabled={busy} onClick={() => setDeleting(null)}>Keep product</Button></div>}>This calls the seller delete endpoint. Its final deletion/archive behavior is determined by the backend.</Alert> : null}
    {loading ? <LoadingBlock label="Loading products" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Product</th><th>Price</th><th>Visibility</th><th>Updated</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{data.content.map((product) => <tr key={product.id}><td><div className="table-product"><ProductImage compact product={product} /><div><strong>{product.name}</strong><span>{product.category || "Uncategorised"}</span></div></div></td><td>{formatMoney(product.price, product.currency)}</td><td><StatusBadge value={product.active === false ? "INACTIVE" : "ACTIVE"} /></td><td>{formatDate(product.updatedAt)}</td><td><div className="action-row"><Link className="button button-ghost" to={`/seller/products/${product.id}/edit`}>Edit</Link><Link className="button button-ghost" to={`/seller/inventory?productId=${encodeURIComponent(product.id)}`}>Stock</Link><Button variant="danger" onClick={() => setDeleting(product)}>Delete</Button></div></td></tr>)}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No seller products yet" message="Create your first product to populate your storefront catalogue." action={<Link className="button button-primary" to="/seller/products/new">Add product</Link>} />}
  </section>;
}

export function SellerProductEditorPage({ mode }: { mode?: "create" | "edit" }) {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const creating = mode === "create" || !productId || productId === "new";
  const { data: existing, loading, error } = useResource(async () => {
    if (creating) return null;
    const page = toPage<Product>(await api.products.sellerList(accessTokenOrThrow(accessToken), "page=0&size=100"));
    const found = page.content.find((product) => product.id === productId);
    if (!found) throw new ApiError("This product could not be found in the first 100 seller records. The backend does not expose an individual seller product endpoint.", 404);
    return found;
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
    <ContractNote>{creating ? "New product creation can trigger asynchronous inventory provisioning. Go to Inventory after saving; a temporary 404 can mean the inventory consumer is still processing." : "This form locates the product through the seller list because no seller-product detail endpoint exists. A product outside the first 100 records cannot be opened after a refresh."}</ContractNote>
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
    <div className="page-heading"><div><span className="eyebrow">{isSeller ? "Seller stock" : "Platform stock"}</span><h1>Inventory by product ID</h1><p>Stock controls operate on one known product at a time.</p></div>{isSeller ? <Link className="button button-secondary" to="/seller/products">Find a product ID</Link> : null}</div>
    <ContractNote>Neither role has an inventory-list or product-search endpoint. This screen intentionally does not invent a stock directory. Enter a known product ID from an authorised workflow.</ContractNote>
    <form className="panel form-stack" onSubmit={(event) => void findInventory(event)}><Field label="Product ID" value={productId} onChange={(event) => setProductId(event.target.value)} placeholder="UUID or product identifier" required /><div className="action-row"><Button type="submit" variant="secondary" loading={busy}>Find inventory</Button></div>{inventory ? <div className="inventory-readout"><div><span>Available stock</span><strong>{inventory.availableStock}</strong></div><div><span>Reserved stock</span><strong>{inventory.reservedStock}</strong></div></div> : null}<Field label="Available stock" type="number" min="0" step="1" value={availableStock} onChange={(event) => setAvailableStock(event.target.value)} hint="Only whole, non-negative units are accepted." />{error ? <Alert tone="danger">{error}</Alert> : null}{message ? <Alert tone="success">{message}</Alert> : null}<div className="action-row"><Button type="button" loading={busy} disabled={!productId.trim() || !availableStock} onClick={() => void save(inventory ? "update" : "create")}>{inventory ? "Update stock" : "Create initial stock"}</Button></div></form>
  </section>;
}

export function SellerInventoryPage() { return <InventoryWorkspace area="seller" />; }

export function SellerOrdersPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const { data, loading, error, reload } = useResource(() => api.orders.sellerList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Order>), [accessToken, pageIndex]);
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Seller orders</span><h1>Order queue</h1><p>Only the seller-scoped order list is available.</p></div></div><ContractNote>This is a read-only seller queue. The address snapshot is sensitive fulfilment context: it is revealed only in this queue, never logged by the browser, and no shipment, cancellation, refund, payout, messaging, or order-detail controls are fabricated.</ContractNote>{loading ? <LoadingBlock label="Loading seller order queue" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Order</th><th>Items</th><th>Seller total</th><th>Status</th><th>Fulfilment address</th><th>Placed</th></tr></thead><tbody>{data.content.map((order) => { const sellerTotal = order.sellerTotalAmount ?? order.sellerSubtotal; return <tr key={order.id}><td><code>{order.id.slice(0, 8)}</code></td><td>{sellerOrderLineCount(order)}</td><td>{sellerTotal === undefined ? "Not supplied" : formatMoney(sellerTotal, order.currency)}</td><td><StatusBadge value={order.status} /></td><td><FulfilmentAddress order={order} /></td><td>{formatDate(order.createdAt)}</td></tr>; })}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No seller order records" message="New seller-scoped order lines will appear here when the backend publishes them." />}</section>;
}

export function AdminOverviewPage() {
  const areas = [
    ["/admin/users", "Users", "Review a known user, status, roles, sessions, and deletion actions."],
    ["/admin/catalogue", "Catalogue", "Create a product for a seller or update one by known ID."],
    ["/admin/inventory", "Inventory", "Create, inspect, or update stock by known product ID."],
    ["/admin/orders", "Orders", "Review list records and submit allowed status transitions."],
    ["/admin/payments", "Payments", "Inspect payment records and request a guarded refund."],
    ["/admin/notifications", "Notifications", "Read redacted failed-delivery diagnostics."],
  ] as const;
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Platform operations</span><h1>Admin workspace</h1><p>Use role-protected tools for the operations that have explicit API contracts.</p></div></div><ContractNote>The backend does not provide an admin dashboard or aggregate metrics endpoint. This overview deliberately avoids fabricated operational numbers.</ContractNote><div className="workspace-grid">{areas.map(([to, title, description]) => <article className="panel link-panel" key={to}><Link to={to}><h2>{title}</h2><span>{description}</span><strong>Open workspace →</strong></Link></article>)}</div></section>;
}

export function AdminUsersPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const { data, loading, error, reload } = useResource(() => api.admin.users(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<User>), [accessToken, pageIndex]);
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Identity operations</span><h1>Users</h1><p>Review only records returned by the role-protected endpoint.</p></div></div><ContractNote>The available API is paginated but does not publish search or filter semantics. Use a known user ID for a detailed record; browser permissions never replace server authorization.</ContractNote>{loading ? <LoadingBlock label="Loading users" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th><span className="sr-only">Open user</span></th></tr></thead><tbody>{data.content.map((record) => <tr key={record.id}><td><strong>{record.name}</strong><br /><code>{record.id.slice(0, 8)}</code></td><td>{record.email}</td><td>{(record.roles?.length ? record.roles : [record.role]).join(", ")}</td><td><StatusBadge value={record.status} /></td><td><Link className="button button-ghost" to={`/admin/users/${encodeURIComponent(record.id)}`}>Open</Link></td></tr>)}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No users returned" message="The API did not return a user record for this page." />}</section>;
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
  const [mode, setMode] = useState<"create" | "edit">(requestedMode ?? "create");
  const [sellerId, setSellerId] = useState("");
  const [productId, setProductId] = useState(routeProductId ?? "");
  const [draft, setDraft] = useState<ProductDraft>(emptyProductDraft);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (requestedMode) setMode(requestedMode);
    if (routeProductId) setProductId(routeProductId);
  }, [requestedMode, routeProductId]);

  async function loadPublicPreview() {
    if (!productId.trim()) { setFeedback({ tone: "danger", text: "Enter a known product ID first." }); return; }
    setBusy(true); setFeedback(null);
    try {
      const product = await api.products.byId(productId.trim());
      setDraft(productToDraft(product)); setFeedback({ tone: "success", text: "Public product data loaded into the form. Inactive products cannot be loaded this way." });
    } catch (caught) { setFeedback({ tone: "warning", text: `${messageForError(caught)} The backend does not provide an admin product detail endpoint.` }); }
    finally { setBusy(false); }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    const validationError = productValidation(draft);
    if (validationError) { setFeedback({ tone: "danger", text: validationError }); return; }
    if (mode === "create" && !sellerId.trim()) { setFeedback({ tone: "danger", text: "A seller ID is required to create an admin-managed product." }); return; }
    if (mode === "edit" && !productId.trim()) { setFeedback({ tone: "danger", text: "A known product ID is required to update a product." }); return; }
    setBusy(true); setFeedback(null);
    try {
      const token = accessTokenOrThrow(accessToken);
      const saved = mode === "create"
        ? await api.products.adminCreate(token, sellerId.trim(), draftToProduct(draft))
        : await api.products.adminUpdate(token, productId.trim(), draftToProduct(draft, true));
      setProductId(saved.id); setMode("edit"); setDraft(productToDraft(saved)); setFeedback({ tone: "success", text: mode === "create" ? `Product created with ID ${saved.id}.` : "Product updated." });
    } catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  async function deleteProduct() {
    if (!productId.trim()) return;
    setBusy(true); setFeedback(null);
    try { await api.products.adminDelete(accessTokenOrThrow(accessToken), productId.trim()); setFeedback({ tone: "success", text: "Delete request completed." }); setConfirmDelete(false); }
    catch (caught) { setFeedback({ tone: "danger", text: messageForError(caught) }); }
    finally { setBusy(false); }
  }

  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Catalogue operations</span><h1>Manage a product by ID</h1><p>Create a product for a known seller or update an existing known product.</p></div></div><ContractNote>There is no admin product list, search, or admin product detail endpoint. A public preview can prefill only an active public product; it is not a replacement for an admin read contract.</ContractNote><div className="toolbar"><Button variant={mode === "create" ? "primary" : "secondary"} onClick={() => { setMode("create"); setConfirmDelete(false); }}>Create product</Button><Button variant={mode === "edit" ? "primary" : "secondary"} onClick={() => { setMode("edit"); setConfirmDelete(false); }}>Edit known product</Button></div><form className="panel form-stack" onSubmit={save}>{mode === "create" ? <Field label="Seller ID" value={sellerId} onChange={(event) => setSellerId(event.target.value)} hint="The admin create API requires the target seller ID." required /> : <div className="form-grid"><Field label="Product ID" value={productId} onChange={(event) => setProductId(event.target.value)} required /><div className="field"><span className="field-label">Public preview</span><Button type="button" variant="secondary" loading={busy} onClick={() => void loadPublicPreview()}>Load active public data</Button></div></div>}<ProductFields draft={draft} setDraft={setDraft} showVisibility={mode === "edit"} />{feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}<div className="action-row"><Button type="submit" loading={busy} disabled={!productIsValid(draft)}>{mode === "create" ? "Create product" : "Save product"}</Button>{mode === "edit" ? (confirmDelete ? <Alert tone="warning" title="Delete this product?" action={<div className="action-row"><Button variant="danger" loading={busy} onClick={() => void deleteProduct()}>Confirm delete</Button><Button variant="secondary" disabled={busy} onClick={() => setConfirmDelete(false)}>Cancel</Button></div>}>The backend determines whether this operation is a delete or deactivation.</Alert> : <Button type="button" variant="danger" disabled={!productId.trim()} onClick={() => setConfirmDelete(true)}>Delete product</Button>) : null}</div></form></section>;
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
  const [targets, setTargets] = useState<Record<string, "CONFIRMED" | "CANCELLED">>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { data, loading, error, reload } = useResource(() => api.orders.adminList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Order>), [accessToken, pageIndex]);
  async function transition(order: Order) {
    const status = targets[order.id] ?? allowedTransitions(order)[0];
    if (!status) return;
    setBusyId(order.id); setActionError(null);
    try { await api.orders.adminUpdateStatus(accessTokenOrThrow(accessToken), order.id, status); await reload(); }
    catch (caught) { setActionError(messageForError(caught)); }
    finally { setBusyId(null); }
  }
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Order operations</span><h1>Orders</h1><p>Submit only the documented lifecycle transitions.</p></div></div><ContractNote>Admin order detail is not available. The API only permits PENDING → CONFIRMED or CANCELLED, and CONFIRMED → CANCELLED. Payment and fulfilment results remain backend-owned.</ContractNote>{actionError ? <Alert tone="danger">{actionError}</Alert> : null}{loading ? <LoadingBlock label="Loading orders" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Order</th><th>Total</th><th>Status</th><th>Placed</th><th>Transition</th></tr></thead><tbody>{data.content.map((order) => { const transitions = allowedTransitions(order); const target = targets[order.id] ?? transitions[0]; return <tr key={order.id}><td><code>{order.id.slice(0, 8)}</code></td><td>{formatMoney(order.totalAmount, order.currency)}</td><td><StatusBadge value={order.status} /></td><td>{formatDate(order.createdAt)}</td><td>{transitions.length ? <div className="action-row"><select className="input compact-input" aria-label={`New status for ${order.id}`} value={target} onChange={(event) => setTargets({ ...targets, [order.id]: event.target.value as "CONFIRMED" | "CANCELLED" })}>{transitions.map((status) => <option key={status} value={status}>{status}</option>)}</select><Button loading={busyId === order.id} onClick={() => void transition(order)}>Apply</Button></div> : <span className="muted">No UI transition</span>}</td></tr>; })}</tbody></table></div><Pagination page={data} onPage={setPageIndex} /></> : <EmptyState title="No orders returned" message="There are no order records on this page." />}</section>;
}

export function AdminPaymentsPage() {
  const { accessToken } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [refunding, setRefunding] = useState(false);
  const idempotencyKey = useRef<string | null>(null);
  const { data: page, loading, error, reload } = useResource(() => api.payments.adminList(accessTokenOrThrow(accessToken), `page=${pageIndex}&size=10`).then(toPage<Payment>), [accessToken, pageIndex]);
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
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Payment operations</span><h1>Payments</h1><p>Inspect a payment before asking the backend to process a refund.</p></div></div><ContractNote>Only list, detail, and refund endpoints are available. A refund request is not an immediate refund result; provider callbacks and backend state are authoritative.</ContractNote><div className="split-layout payment-layout"><section>{loading ? <LoadingBlock label="Loading payments" /> : error ? <PageError message={error} retry={() => void reload()} /> : page?.content.length ? <><div className="table-wrap"><table className="data-table"><thead><tr><th>Payment</th><th>Order</th><th>Amount</th><th>Status</th><th></th></tr></thead><tbody>{page.content.map((payment) => <tr key={payment.id}><td><code>{payment.id.slice(0, 8)}</code></td><td><code>{payment.orderId.slice(0, 8)}</code></td><td>{payment.amount === undefined ? "Not supplied" : formatMoney(payment.amount, payment.currency)}</td><td><StatusBadge value={payment.status} /></td><td><Button variant="ghost" onClick={() => setSelectedId(payment.id)}>Inspect</Button></td></tr>)}</tbody></table></div><Pagination page={page} onPage={setPageIndex} /></> : <EmptyState title="No payments returned" message="The API did not return a payment record for this page." />}</section><aside className="panel form-stack"><h2>Payment detail</h2>{!selectedId ? <p className="muted">Select a payment to request its server-backed detail.</p> : detailLoading ? <LoadingBlock label="Loading payment" /> : detailError || !detail ? <Alert tone="danger">{detailError || "Payment detail was unavailable."}</Alert> : <><dl className="detail-list"><div><dt>Payment ID</dt><dd><code>{detail.id}</code></dd></div><div><dt>Order ID</dt><dd><code>{detail.orderId}</code></dd></div><div><dt>Status</dt><dd><StatusBadge value={detail.status} /></dd></div><div><dt>Amount</dt><dd>{detail.amount === undefined ? "Not supplied" : formatMoney(detail.amount, detail.currency)}</dd></div><div><dt>Provider</dt><dd>{detail.provider || "Not supplied"}</dd></div></dl><form className="form-stack" onSubmit={requestRefund}><h3>Request refund</h3><Field label="Amount" type="number" min="0.01" step="0.01" value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} required /><Field label="Reason (optional)" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} maxLength={200} />{feedback ? <Alert tone={feedback.tone}>{feedback.text}</Alert> : null}<Button type="submit" variant="danger" loading={refunding}>Request refund</Button></form></>}</aside></div></section>;
}

export function AdminNotificationsPage() {
  const { accessToken } = useAuth();
  const { data, loading, error, reload } = useResource(() => api.admin.failedNotifications(accessTokenOrThrow(accessToken)), [accessToken]);
  return <section className="backoffice-page"><div className="page-heading"><div><span className="eyebrow">Operational diagnostics</span><h1>Failed notifications</h1><p>A minimally exposed diagnostic view for authorised administrators.</p></div></div><ContractNote>The notification endpoint can contain operational or personal data. This client intentionally redacts recipient and message fields. Dead-letter replay is not wired because it needs a dedicated audited, confirmation-based workflow.</ContractNote>{loading ? <LoadingBlock label="Loading failed notification diagnostics" /> : error ? <PageError message={error} retry={() => void reload()} /> : data?.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Diagnostic ID</th><th>Type</th><th>Status</th><th>Recorded</th><th>Content</th></tr></thead><tbody>{data.map((notification) => <tr key={notification.id}><td><code>{notification.id.slice(0, 8)}…</code></td><td>{notification.type || "Not supplied"}</td><td><StatusBadge value={notification.status || "FAILED"} /></td><td>{formatDate(notification.createdAt)}</td><td><span className="muted">Redacted</span></td></tr>)}</tbody></table></div> : <EmptyState title="No failed notifications" message="No failed-delivery records were returned by the endpoint." />}</section>;
}
