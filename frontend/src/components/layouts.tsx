import { useEffect, useRef, type FormEvent, type MouseEvent } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { ArrowUpRight, ChevronDown, ClipboardList, LayoutDashboard, LogOut, Search, ShoppingBag, Store, UserRound } from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { useCart } from "../cart/CartProvider";
import "../styles/storefront-shell.css";

const storefrontCategories = ["Apparel", "Home", "Accessories", "Stationery"] as const;

export function StorefrontLayout() {
  const { user, isAuthenticated, hasAnyRole, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const previousPathname = useRef<string | null>(null);
  const searchParams = new URLSearchParams(location.search);
  const selectedCategory = location.pathname === "/" ? searchParams.get("category") : null;
  const itemCount = cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;

  useEffect(() => {
    const pathChanged = previousPathname.current !== location.pathname;
    if (!location.hash && !pathChanged) return;

    const frame = requestAnimationFrame(() => {
      // Commit after the frame so a StrictMode cleanup can safely reschedule it.
      previousPathname.current = location.pathname;
      if (location.hash) {
        const target = document.getElementById(location.hash.slice(1));
        target?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
        target?.focus({ preventScroll: true });
      } else {
        window.scrollTo({ top: 0, behavior: "auto" });
        document.getElementById("store-content")?.focus({ preventScroll: true });
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [location.hash, location.key, location.pathname]);

  async function signOut() {
    await logout();
    navigate("/");
  }

  function searchCatalogue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = String(new FormData(event.currentTarget).get("q") ?? "").trim();
    const next = new URLSearchParams();
    if (query) next.set("q", query);
    navigate(`/${next.size ? `?${next.toString()}` : ""}#catalogue`);
  }

  function closeAccountMenu(event: MouseEvent<HTMLElement>) {
    event.currentTarget.closest("details")?.removeAttribute("open");
  }

  const categoryLinks = <>
    <Link className={location.pathname === "/" && !selectedCategory ? "is-current" : undefined} to="/#catalogue">Shop all</Link>
    {storefrontCategories.map(category => <Link key={category} className={selectedCategory === category ? "is-current" : undefined} to={`/?category=${encodeURIComponent(category)}#catalogue`}>{category}</Link>)}
  </>;

  return <div className="app-shell storefront-v2">
    <a className="sf-skip-link" href="#store-content">Skip to content</a>
    <div className="sf-announcement"><span>Everyday finds. A little more you.</span><Link to="/#catalogue">Explore the collection <ArrowUpRight size={13} aria-hidden="true" /></Link></div>
    <header className="sf-header">
      <div className="sf-header-inner">
        <Link className="sf-wordmark" to="/" aria-label="Pepekart home">pepekart<span aria-hidden="true">.</span></Link>
        <nav className="sf-desktop-nav" aria-label="Main navigation">{categoryLinks}</nav>
        <form className="sf-search" role="search" onSubmit={searchCatalogue}>
          <input key={`${location.pathname}${location.search}`} type="search" name="q" defaultValue={location.pathname === "/" ? searchParams.get("q") ?? "" : ""} placeholder="Find your next favourite" aria-label="Search products" maxLength={200} />
          <button type="submit" aria-label="Search"><Search size={18} strokeWidth={1.7} aria-hidden="true" /></button>
        </form>
        <div className="sf-header-actions">
          {isAuthenticated && user ? <details className="sf-account-menu" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.removeAttribute("open"); }} onKeyDown={event => { if (event.key === "Escape") { event.currentTarget.removeAttribute("open"); event.currentTarget.querySelector("summary")?.focus(); } }}>
            <summary aria-label={`Account menu for ${user.name}`}><UserRound size={21} strokeWidth={1.6} aria-hidden="true" /><span className="sf-account-label">{user.name.split(" ")[0]}</span><ChevronDown size={12} aria-hidden="true" /></summary>
            <div className="sf-account-popover">
              <div className="sf-account-identity"><strong>{user.name}</strong><span>{user.email}</span></div>
              <Link to="/account" onClick={closeAccountMenu}><UserRound size={16} aria-hidden="true" />My account</Link>
              <Link to="/orders" onClick={closeAccountMenu}><ClipboardList size={16} aria-hidden="true" />My orders</Link>
              {hasAnyRole("SELLER", "ADMIN") ? <Link to="/seller" onClick={closeAccountMenu}><Store size={16} aria-hidden="true" />Seller workspace</Link> : null}
              {hasAnyRole("ADMIN") ? <Link to="/admin" onClick={closeAccountMenu}><LayoutDashboard size={16} aria-hidden="true" />Admin workspace</Link> : null}
              <button className="sf-signout" onClick={event => { closeAccountMenu(event); void signOut(); }}><LogOut size={16} aria-hidden="true" />Sign out</button>
            </div>
          </details> : <Link className="sf-account-link" to="/login" aria-label="Sign in"><UserRound size={21} strokeWidth={1.6} aria-hidden="true" /><span className="sf-account-label">Sign in</span></Link>}
          <Link className="sf-bag-link" to="/cart" aria-label={`Shopping bag, ${itemCount} ${itemCount === 1 ? "item" : "items"}`}><ShoppingBag size={21} strokeWidth={1.6} aria-hidden="true" /><span className="sf-bag-count">{itemCount > 99 ? "99+" : itemCount}</span></Link>
        </div>
      </div>
      <nav className="sf-mobile-categories" aria-label="Browse categories">{categoryLinks}</nav>
    </header>
    <main className="store-main" id="store-content" tabIndex={-1}><Outlet /></main>
    <footer className="sf-footer">
      <div className="sf-footer-inner">
        <div className="sf-footer-intro"><Link className="sf-wordmark" to="/">pepekart<span aria-hidden="true">.</span></Link><p>A little something<br />for your everyday.</p><span>Good finds for your wardrobe, your home,<br className="sf-desktop-break" /> and all the moments in between.</span></div>
        <nav className="sf-footer-links" aria-label="Shop collections"><h2>Make yourself at home</h2>{storefrontCategories.map(category => <Link key={category} to={`/?category=${encodeURIComponent(category)}#catalogue`}>{category}<ArrowUpRight size={14} aria-hidden="true" /></Link>)}</nav>
        <nav className="sf-footer-links" aria-label="Your Pepekart"><h2>Your Pepekart</h2><Link to="/account">My account<ArrowUpRight size={14} aria-hidden="true" /></Link><Link to="/orders">My orders<ArrowUpRight size={14} aria-hidden="true" /></Link><Link to="/cart">Shopping bag<ArrowUpRight size={14} aria-hidden="true" /></Link>{!isAuthenticated ? <Link to="/register">Create an account<ArrowUpRight size={14} aria-hidden="true" /></Link> : <Link to="/#catalogue">Discover more<ArrowUpRight size={14} aria-hidden="true" /></Link>}</nav>
        <div className="sf-footer-bottom"><span>&copy; {new Date().getFullYear()} Pepekart</span><span>Final price and availability confirmed at checkout.</span><span>Made for the everyday.</span></div>
      </div>
    </footer>
  </div>;
}

export { BackofficeLayout } from "./BackofficeExperience";
