import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { ArrowRight, ArrowUpRight, Bell, Boxes, ClipboardList, CreditCard, LayoutDashboard, LogOut, Menu, Package, ShieldCheck, Store, Users, X } from "lucide-react";
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
      <div className="bo-brand-row"><Link className="bo-brand" to="/">pepekart<span>.</span></Link><button className="bo-menu-close" type="button" onClick={closeMenu} aria-label="Close navigation"><X size={20} /></button></div>
      <div className="bo-workspace-label"><span className="bo-workspace-icon">{area === "seller" ? <Store size={18} /> : <ShieldCheck size={18} />}</span><div><strong>{label}</strong><span>{area === "seller" ? "Your business, organised" : "Platform management"}</span></div></div>
      <span className="bo-nav-caption">WORKSPACE</span>
      <nav ref={navRef} id="workspace-navigation" aria-label={label + " navigation"}>
        {navigation.map(([path, text, NavIcon]) => <NavLink key={path} to={path} end={path === "/" + area} onClick={() => setMenuOpen(false)}><NavIcon size={18} strokeWidth={1.7} aria-hidden="true" /><span>{text}</span><ArrowRight className="bo-nav-arrow" size={14} aria-hidden="true" /></NavLink>)}
      </nav>
      <div className="bo-sidebar-links"><Link to="/"><Store size={17} aria-hidden="true" />Visit storefront<ArrowUpRight size={14} aria-hidden="true" /></Link>{area === "seller" && hasAnyRole("ADMIN") ? <Link to="/admin"><ShieldCheck size={17} aria-hidden="true" />Admin workspace</Link> : area === "admin" && hasAnyRole("SELLER", "ADMIN") ? <Link to="/seller"><Boxes size={17} aria-hidden="true" />Seller workspace</Link> : null}</div>
      <div className="bo-profile"><span className="bo-avatar" aria-hidden="true">{user?.name?.slice(0, 1).toUpperCase() || "P"}</span><div><strong>{user?.name || label}</strong><span>{user?.email}</span></div><button type="button" disabled={signingOut} onClick={() => void signOut()} aria-label={signingOut ? "Signing out" : "Sign out"}><LogOut size={18} aria-hidden="true" /></button></div>
    </aside>
    <div className="bo-main-column">
      <header className="bo-topbar"><div className="bo-location"><button ref={toggleRef} className="bo-menu-toggle" type="button" aria-label={menuOpen ? "Close workspace navigation" : "Open workspace navigation"} aria-expanded={menuOpen} aria-controls="workspace-navigation" onClick={menuOpen ? closeMenu : openMenu}><Menu size={22} aria-hidden="true" /></button><Icon size={18} aria-hidden="true" /><span>{area === "seller" ? "Seller" : "Admin"}<span className="bo-breadcrumb-divider">/</span><strong>{active[1]}</strong></span></div><div className="bo-topbar-actions"><Link to="/" className="bo-store-link">View store<ArrowUpRight size={15} aria-hidden="true" /></Link><span className="bo-role-label">{area}</span></div></header>
      <main id="workspace-content" className="bo-content" tabIndex={-1}>{error ? <Alert tone="danger">{error}</Alert> : null}<Outlet /></main>
      <footer className="bo-footer"><span>Pepekart / {label}</span><span>Built around your everyday operations.</span></footer>
    </div>
  </div>;
}
