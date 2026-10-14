import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { ArrowRight, ArrowUpRight, Bell, Boxes, ClipboardList, CreditCard, LayoutDashboard, LogOut, Menu, Package, ShieldCheck, ShoppingBag, Store, Users, X } from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { Alert } from "./ui";
import { messageForError } from "../lib/format";
import "../styles/backoffice-experience.css";

const sellerNavigation = [
  ["/seller", "Overview", LayoutDashboard],
  ["/seller/products", "Products", Package],
  ["/seller/inventory", "Inventory", Boxes],
  ["/seller/orders", "Orders", ClipboardList],
] as const;
const adminNavigation = [
  ["/admin", "Overview", LayoutDashboard],
  ["/admin/users", "Users & access", Users],
  ["/admin/catalogue", "Catalogue", Package],
  ["/admin/inventory", "Inventory", Boxes],
  ["/admin/orders", "Orders", ClipboardList],
  ["/admin/payments", "Payments", CreditCard],
  ["/admin/notifications", "Notifications", Bell],
] as const;

export function BackofficeLayout({ area }: { area: "seller" | "admin" }) {
  const { user, logout, hasAnyRole } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const navRef = useRef<HTMLElement>(null);
  const navigation = area === "seller" ? sellerNavigation : adminNavigation;
  const label = area === "seller" ? "Seller workspace" : "Admin workspace";
  const navigationGroups = area === "seller"
    ? [{ label: "WORKSPACE", items: navigation.slice(0, 1) }, { label: "CATALOGUE", items: navigation.slice(1, 3) }, { label: "COMMERCE", items: navigation.slice(3) }]
    : [{ label: "WORKSPACE", items: navigation.slice(0, 1) }, { label: "PEOPLE & CATALOGUE", items: navigation.slice(1, 4) }, { label: "COMMERCE", items: navigation.slice(4, 6) }, { label: "OPERATIONS", items: navigation.slice(6) }];
  const active = [...navigation].reverse().find(([path]) => location.pathname === path || location.pathname.startsWith(path + "/")) ?? navigation[0];
  const Icon = active[2];

  useEffect(() => {
    setMenuOpen(false);
    const frame = requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: "auto" });
      document.getElementById("workspace-content")?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [location.pathname]);

  function closeMenu() { setMenuOpen(false); toggleRef.current?.focus(); }
  function openMenu() {
    setMenuOpen(true);
    requestAnimationFrame(() => navRef.current?.querySelector<HTMLAnchorElement>("a[aria-current], a")?.focus());
  }
  async function signOut() {
    setSigningOut(true); setError(null);
    try { await logout(); navigate("/"); }
    catch (caught) { setError(messageForError(caught)); setSigningOut(false); }
  }

  return <div className={"bo-shell bo-" + area}>
    <a className="bo-skip-link" href="#workspace-content">Skip to workspace content</a>
    <aside className={"bo-sidebar" + (menuOpen ? " is-open" : "")} onKeyDown={event => { if (event.key === "Escape") closeMenu(); }}>
      <div className="bo-brand-row"><Link className="bo-brand" to="/"><span className="bo-brand-mark"><ShoppingBag size={21} strokeWidth={1.8} aria-hidden="true" /></span>pepekart<span className="bo-brand-dot">.</span></Link><button className="bo-menu-close" type="button" onClick={closeMenu} aria-label="Close navigation"><X size={20} /></button></div>
      <div className="bo-workspace-label"><span className="bo-workspace-icon">{area === "seller" ? <Store size={18} /> : <ShieldCheck size={18} />}</span><div><strong>{label}</strong><span>{area === "seller" ? "Business console" : "Operations console"}</span></div></div>
      <nav ref={navRef} id="workspace-navigation" aria-label={label + " navigation"}>
        {navigationGroups.map(group => <div className="bo-nav-group" key={group.label}><span className="bo-nav-caption">{group.label}</span>{group.items.map(([path, text, NavIcon]) => <NavLink key={path} to={path} end={path === "/" + area} onClick={() => setMenuOpen(false)}><NavIcon size={18} strokeWidth={1.7} aria-hidden="true" /><span>{text}</span><ArrowRight className="bo-nav-arrow" size={14} aria-hidden="true" /></NavLink>)}</div>)}
      </nav>
      <div className="bo-sidebar-links"><Link to="/"><Store size={17} aria-hidden="true" />Visit storefront<ArrowUpRight size={14} aria-hidden="true" /></Link>{area === "seller" && hasAnyRole("ADMIN") ? <Link to="/admin"><ShieldCheck size={17} aria-hidden="true" />Admin workspace</Link> : area === "admin" && hasAnyRole("SELLER", "ADMIN") ? <Link to="/seller"><Boxes size={17} aria-hidden="true" />Seller workspace</Link> : null}</div>
      <div className="bo-profile"><span className="bo-avatar" aria-hidden="true">{user?.name?.slice(0, 1).toUpperCase() || "P"}</span><div><strong>{user?.name || label}</strong><span>{user?.email}</span></div><button type="button" disabled={signingOut} onClick={() => void signOut()} aria-label={signingOut ? "Signing out" : "Sign out"}><LogOut size={18} aria-hidden="true" /></button></div>
    </aside>
    <div className="bo-main-column">
      <header className="bo-topbar"><div className="bo-location"><button ref={toggleRef} className="bo-menu-toggle" type="button" aria-label={menuOpen ? "Close workspace navigation" : "Open workspace navigation"} aria-expanded={menuOpen} aria-controls="workspace-navigation" onClick={menuOpen ? closeMenu : openMenu}><Menu size={22} aria-hidden="true" /></button><span className="bo-location-icon"><Icon size={18} aria-hidden="true" /></span><div className="bo-location-copy"><span>Pepekart console</span><strong>{area === "seller" ? "Seller" : "Admin"}<span className="bo-breadcrumb-divider">/</span>{active[1]}</strong></div></div><div className="bo-topbar-actions"><span className="bo-role-label">{area}</span><Link to="/" className="bo-store-link"><Store size={15} aria-hidden="true" />Open storefront<ArrowUpRight size={15} aria-hidden="true" /></Link></div></header>
      <main id="workspace-content" className="bo-content" tabIndex={-1}>{error ? <Alert tone="danger">{error}</Alert> : null}<Outlet /></main>
      <footer className="bo-footer"><span>Pepekart / {label}</span><span>Built around your everyday operations.</span></footer>
    </div>
  </div>;
}
