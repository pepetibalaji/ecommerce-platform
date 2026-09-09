import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { ArrowLeft, Bell, Boxes, ClipboardList, CreditCard, LayoutDashboard, LogOut, Package, ShoppingBag, Store, UserRound, Users } from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import { Button, StatusBadge } from "./ui";

const storefrontNavigation = [
  ["/", "Shop", Store],
  ["/orders", "Orders", ClipboardList],
  ["/account", "Account", UserRound],
] as const;

export function StorefrontLayout() {
  const { user, isAuthenticated, hasAnyRole, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const itemCount = cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;

  async function signOut() {
    await logout();
    navigate("/");
  }

  return <div className="app-shell">
    <header className="store-header">
      <Link className="brand" to="/"><Store className="brand-icon" size={20} strokeWidth={2.4} aria-hidden="true" />marketly<span>.</span></Link>
      <nav className="store-nav" aria-label="Main navigation">
        {storefrontNavigation.filter(([to]) => to === "/" || isAuthenticated).map(([to, label, Icon]) => <NavLink key={to} to={to} end={to === "/"}><Icon size={15} aria-hidden="true" /><span>{label}</span></NavLink>)}
        {hasAnyRole("SELLER", "ADMIN") ? <NavLink to="/seller"><Store size={15} aria-hidden="true" /><span>Seller</span></NavLink> : null}
        {hasAnyRole("ADMIN") ? <NavLink to="/admin"><LayoutDashboard size={15} aria-hidden="true" /><span>Admin</span></NavLink> : null}
      </nav>
      <div className="header-actions">
        <Link className="cart-link" to="/cart"><ShoppingBag size={18} aria-hidden="true" /><span className="cart-label">Cart</span><span className="cart-count">{itemCount}</span></Link>
        {isAuthenticated && user ? <details className="profile-menu"><summary><span className="profile-avatar">{user.name.slice(0, 1).toUpperCase()}</span>{user.name.split(" ")[0]} <StatusBadge value={user.role} /></summary><div className="profile-popover"><p>{user.email}</p><Button variant="ghost" onClick={() => void signOut()}><LogOut size={15} aria-hidden="true" />Sign out</Button></div></details> : <Link className="button button-primary" to="/login">Sign in</Link>}
      </div>
    </header>
    <nav className="mobile-store-nav" aria-label="Mobile navigation">
      {storefrontNavigation.filter(([to]) => to === "/" || isAuthenticated).map(([to, label, Icon]) => <NavLink key={to} to={to} end={to === "/"}><Icon size={17} aria-hidden="true" /><span>{label}</span></NavLink>)}
      {hasAnyRole("SELLER", "ADMIN") ? <NavLink to="/seller"><Store size={17} aria-hidden="true" /><span>Seller</span></NavLink> : null}
      {hasAnyRole("ADMIN") ? <NavLink to="/admin"><LayoutDashboard size={17} aria-hidden="true" /><span>Admin</span></NavLink> : null}
    </nav>
    <main className="store-main"><Outlet /></main>
    <footer className="store-footer"><span>Marketly stage environment</span><span>Final price and availability are confirmed at checkout.</span></footer>
  </div>;
}

const sellerNav = [
  ["/seller", "Overview", LayoutDashboard],
  ["/seller/products", "Products", Package],
  ["/seller/inventory", "Inventory", Boxes],
  ["/seller/orders", "Order queue", ClipboardList],
] as const;

const adminNav = [
  ["/admin", "Overview", LayoutDashboard],
  ["/admin/users", "Users", Users],
  ["/admin/catalogue", "Catalogue", Package],
  ["/admin/inventory", "Inventory", Boxes],
  ["/admin/orders", "Orders", ClipboardList],
  ["/admin/payments", "Payments", CreditCard],
  ["/admin/notifications", "Notifications", Bell],
] as const;

export function BackofficeLayout({ area }: { area: "seller" | "admin" }) {
  const navigation = area === "seller" ? sellerNav : adminNav;
  const label = area === "seller" ? "Seller workspace" : "Admin workspace";
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return <div className="backoffice-shell">
    <aside className="backoffice-sidebar">
      <Link className="brand brand-inverse" to="/"><Store className="brand-icon" size={20} strokeWidth={2.4} aria-hidden="true" />marketly<span>.</span></Link>
      <div className="workspace-label">{label}</div>
      <nav aria-label={`${label} navigation`}>
        {navigation.map(([to, text, Icon]) => <NavLink key={to} to={to} end={to === `/${area}`}><Icon size={17} strokeWidth={1.9} aria-hidden="true" /><span>{text}</span></NavLink>)}
      </nav>
      <div className="sidebar-profile"><span className="sidebar-avatar">{user?.name.slice(0, 1).toUpperCase()}</span><strong>{user?.name}</strong><span>{user?.email}</span><Button variant="ghost" onClick={async () => { await logout(); navigate("/"); }}><LogOut size={15} aria-hidden="true" />Sign out</Button></div>
    </aside>
    <main className="backoffice-main"><div className="backoffice-topbar"><Link to="/"><ArrowLeft size={16} aria-hidden="true" />Storefront</Link><StatusBadge value={area.toUpperCase()} /></div><Outlet /></main>
  </div>;
}
