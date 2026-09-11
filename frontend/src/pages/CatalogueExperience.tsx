import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowDown, ArrowRight, ArrowUp, Check, ChevronDown, LayoutGrid, LoaderCircle, Plus, Search, ShieldCheck, SlidersHorizontal, X } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { useCart } from "../cart/CartProvider";
import type { Product } from "../domain";
import { api } from "../lib/api";
import { formatMoney, messageForError } from "../lib/format";
import { useResource } from "../lib/hooks";
import { useInfiniteProducts } from "../lib/useInfiniteProducts";
import { Alert, Button, EmptyState, Field, ProductImage, SelectField } from "../components/ui";
import "../styles/catalogue-experience.css";

const filterKeys = ["q", "category", "brand", "minPrice", "maxPrice"] as const;
type FilterKey = typeof filterKeys[number];
type FilterDraft = Record<FilterKey, string>;
const collections = [
  { name: "Apparel", caption: "Wear it your way", image: "https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/thumbnail.webp", tone: "sand" },
  { name: "Home", caption: "Make yourself at home", image: "https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp", tone: "sage" },
  { name: "Accessories", caption: "The finishing touches", image: "https://cdn.dummyjson.com/product-images/womens-bags/heshe-women's-leather-bag/thumbnail.webp", tone: "rose" },
  { name: "Stationery", caption: "Room for your ideas", image: "https://images.pexels.com/photos/5594263/pexels-photo-5594263.jpeg?auto=compress&cs=tinysrgb&w=600", tone: "paper" },
];

function readDraft(params: URLSearchParams): FilterDraft {
  return Object.fromEntries(filterKeys.map(key => [key, params.get(key) ?? ""])) as FilterDraft;
}

function focusCollection() {
  requestAnimationFrame(() => {
    const target = document.getElementById("catalogue");
    target?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
    target?.focus({ preventScroll: true });
  });
}

function CollectionImage({ src, name }: { src: string; name: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <span className="shop-collection-monogram" aria-hidden="true">{name.slice(0, 1)}</span> : <img src={src} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />;
}

function ShopProductCard({ product }: { product: Product }) {
  const { add } = useCart();
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function addToBag() {
    if (adding) return;
    setAdding(true); setAdded(false); setError(null);
    try { await add(product.id); setAdded(true); }
    catch (caught) { setError(messageForError(caught)); }
    finally { setAdding(false); }
  }

  return <article className="shop-product">
    <Link className="shop-product-visual" to={`/products/${product.id}`} aria-label={`View ${product.name}`}>
      <ProductImage product={product} />
      <span className="shop-product-category">{product.category || "Discover"}</span>
      <span className="shop-product-view">Take a closer look <ArrowRight size={16} aria-hidden="true" /></span>
    </Link>
    <div className="shop-product-info">
      <span className="shop-product-brand">{product.brand && product.brand !== "Unbranded" ? product.brand : product.category || "Pepekart"}</span>
      <h3><Link to={`/products/${product.id}`}>{product.name}</Link></h3>
      <div className="shop-product-purchase">
        <strong>{formatMoney(product.price, product.currency)}</strong>
        <button className={`shop-quick-add ${added ? "is-added" : ""}`} type="button" disabled={adding || product.active === false} onClick={() => void addToBag()} aria-label={`Add ${product.name} to bag`}>
          {adding ? <LoaderCircle className="shop-spin" size={16} aria-hidden="true" /> : added ? <Check size={16} aria-hidden="true" /> : <Plus size={16} aria-hidden="true" />}
          <span>{adding ? "Adding" : added ? "Add again" : "Add to bag"}</span>
        </button>
      </div>
      {added ? <p className="shop-card-feedback" role="status">Added to your bag. <Link to="/cart">View bag</Link></p> : null}
      {error ? <p className="shop-card-error" role="alert">{error}</p> : null}
    </div>
  </article>;
}

function ProductSkeletons({ count = 12 }: { count?: number }) {
  return <>{Array.from({ length: count }, (_, index) => <div className="shop-product shop-skeleton" key={index} aria-hidden="true"><div className="shop-product-visual" /><div className="shop-skeleton-line short" /><div className="shop-skeleton-line" /><div className="shop-skeleton-line medium" /></div>)}</>;
}

export function CataloguePage() {
  const [params, setParams] = useSearchParams();
  const filterParams = new URLSearchParams();
  for (const key of [...filterKeys, "sort"]) if (params.get(key)) filterParams.set(key, params.get(key)!);
  const query = filterParams.toString();
  const [draft, setDraft] = useState(() => readDraft(params));
  const [filtersOpen, setFiltersOpen] = useState(false);
  const filterToggleRef = useRef<HTMLButtonElement>(null);
  const [heroFailed, setHeroFailed] = useState(false);
  const { data: facets } = useResource(() => api.products.facets(), []);
  const { products, totalElements, loading, loadingMore, error, loadMoreError, hasMore, loadMore, retry, sentinelRef } = useInfiniteProducts(query);
  const categoryFacets = facets?.categories ?? collections.map(collection => ({ name: collection.name, count: 0 }));
  const brands = facets?.brands ?? [];
  const appliedFilters = filterKeys.filter(key => Boolean(params.get(key)));
  const activeCategory = params.get("category") ?? "";
  const hasFilters = appliedFilters.length > 0;
  const badPriceRange = Boolean(draft.minPrice && draft.maxPrice && Number(draft.minPrice) > Number(draft.maxPrice));
  const title = params.get("q") ? `Results for “${params.get("q")}”` : activeCategory || "Your next good find.";
  const CatalogueHeading = hasFilters ? "h1" : "h2";

  useEffect(() => { setDraft(readDraft(new URLSearchParams(query))); }, [query]);

  function updateFilters(next: URLSearchParams) {
    next.delete("page"); next.delete("size");
    setParams(next);
    focusCollection();
  }

  function selectCategory(category: string) {
    const next = new URLSearchParams(filterParams);
    if (category) next.set("category", category); else next.delete("category");
    updateFilters(next);
  }

  function closeFilters() {
    setFiltersOpen(false);
    filterToggleRef.current?.focus({ preventScroll: true });
  }

  function toggleFilters() {
    if (filtersOpen) closeFilters();
    else { setFiltersOpen(true); focusCollection(); }
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams(filterParams);
    for (const key of filterKeys) {
      const value = draft[key].trim();
      if (value) next.set(key, value); else next.delete(key);
    }
    updateFilters(next);
    closeFilters();
  }

  function search(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams(filterParams);
    if (draft.q.trim()) next.set("q", draft.q.trim()); else next.delete("q");
    updateFilters(next);
  }

  function clearFilters() { setParams(new URLSearchParams()); closeFilters(); focusCollection(); }

  function removeFilter(key: FilterKey) {
    const next = new URLSearchParams(filterParams); next.delete(key); updateFilters(next);
  }

  return <div className="shop-experience">
    {!hasFilters ? <>
      <section className="shop-hero" aria-labelledby="shop-hero-title">
        <div className="shop-hero-copy">
          <span className="shop-kicker"><span /> THE EVERYDAY EDIT</span>
          <h1 id="shop-hero-title">Good things.<br />For your <em>everyday.</em></h1>
          <p>A little more you. Discover pieces for your wardrobe, your home, and everything in between.</p>
          <div className="shop-hero-actions"><a className="shop-primary-link" href="#catalogue">Explore the collection <ArrowRight size={18} aria-hidden="true" /></a><button className="shop-text-button" type="button" onClick={() => selectCategory("Home")}>Make yourself at home</button></div>
          <div className="shop-hero-caption"><span>01 — Discover</span><span>A fresh perspective on the everyday.</span></div>
        </div>
        <div className={`shop-hero-scene ${heroFailed ? "is-unavailable" : ""}`}>
          {!heroFailed ? <img src="https://images.pexels.com/photos/1571460/pexels-photo-1571460.jpeg?auto=compress&cs=tinysrgb&w=1400" alt="A light-filled living room with natural textures and a comfortable sofa" fetchPriority="high" onError={() => setHeroFailed(true)} /> : <span className="shop-hero-fallback">Make space<br />for good things.</span>}
          <span className="shop-scene-label">THE HOME COLLECTION</span>
          <button className="shop-scene-link" type="button" onClick={() => selectCategory("Home")}><span>Small details.<strong>A place that feels like you.</strong></span><ArrowRight size={22} aria-hidden="true" /></button>
        </div>
      </section>
      <div className="shop-values"><span><LayoutGrid size={17} aria-hidden="true" /> One shop. So much to discover.</span><span><ShieldCheck size={17} aria-hidden="true" /> A clear, simple checkout.</span><span><ArrowDown size={17} aria-hidden="true" /> Keep scrolling. Keep discovering.</span></div>
      <section className="shop-collections" aria-labelledby="shop-collections-title">
        <div className="shop-section-heading"><div><span className="shop-kicker">A GOOD PLACE TO START</span><h2 id="shop-collections-title">Find your kind of good.</h2></div><a href="#catalogue">Shop everything <ArrowRight size={17} aria-hidden="true" /></a></div>
        <div className="shop-collection-grid">{collections.map((collection, index) => {
          const count = categoryFacets.find(facet => facet.name === collection.name)?.count;
          return <button className={`shop-collection shop-collection-${collection.tone}`} key={collection.name} type="button" onClick={() => selectCategory(collection.name)}>
            <span className="shop-collection-number">0{index + 1}</span><div className="shop-collection-photo"><CollectionImage src={collection.image} name={collection.name} /></div>
            <div className="shop-collection-copy"><span><strong>{collection.name}</strong><small>{collection.caption}{count ? ` · ${count} finds` : ""}</small></span><ArrowRight size={20} aria-hidden="true" /></div>
          </button>;
        })}</div>
      </section>
    </> : <div className="shop-breadcrumb"><Link to="/">Shop</Link><span>/</span><span>{activeCategory || "Your selection"}</span></div>}

    <section className="shop-catalogue" id="catalogue" tabIndex={-1} aria-labelledby="shop-catalogue-title">
      <div className="shop-section-heading shop-results-heading"><div><span className="shop-kicker">{hasFilters ? "MADE PERSONAL" : "THE COLLECTION"}</span><CatalogueHeading id="shop-catalogue-title">{title}</CatalogueHeading></div><p role="status">{loading ? "Finding your next favorite…" : error ? "Collection temporarily unavailable" : `${totalElements.toLocaleString("en-IN")} ${totalElements === 1 ? "product" : "products"} to explore`}</p></div>
      <div className="shop-toolbar">
        <form className="shop-search" role="search" aria-label="Search products" onSubmit={search}><Search size={19} aria-hidden="true" /><input aria-label="Search products" type="search" placeholder="What are you looking for?" value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} /><button type="submit" aria-label="Submit product search"><ArrowRight size={18} aria-hidden="true" /></button></form>
        <button ref={filterToggleRef} className={`shop-filter-toggle ${filtersOpen ? "is-open" : ""}`} type="button" aria-expanded={filtersOpen} aria-controls={filtersOpen ? "shop-filter-panel" : undefined} onClick={toggleFilters}><SlidersHorizontal size={17} aria-hidden="true" />Filters{appliedFilters.length ? <span>{appliedFilters.length}</span> : null}</button>
        <label className="shop-sort"><span className="sr-only">Sort products</span><select value={params.get("sort") ?? "newest"} onChange={event => { const next = new URLSearchParams(filterParams); next.set("sort", event.target.value); updateFilters(next); }}><option value="newest">Newest first</option><option value="price_asc">Price: low to high</option><option value="price_desc">Price: high to low</option><option value="name_asc">Name: A–Z</option><option value="name_desc">Name: Z–A</option></select><ChevronDown size={15} aria-hidden="true" /></label>
      </div>
      {filtersOpen ? <form className="shop-filter-panel" id="shop-filter-panel" onSubmit={applyFilters} onKeyDown={event => { if (event.key === "Escape") closeFilters(); }}>
        <div className="shop-filter-panel-heading"><h3>A little more specific.</h3><button type="button" aria-label="Close filters" onClick={closeFilters}><X size={18} aria-hidden="true" /></button></div>
        <div className="shop-filter-fields"><SelectField label="Category" value={draft.category} onChange={event => setDraft({ ...draft, category: event.target.value })}><option value="">All categories</option>{categoryFacets.map(facet => <option key={facet.name} value={facet.name}>{facet.name}{facet.count ? ` (${facet.count})` : ""}</option>)}</SelectField><SelectField label="Brand" value={draft.brand} onChange={event => setDraft({ ...draft, brand: event.target.value })}><option value="">All brands</option>{brands.map(facet => <option key={facet.name} value={facet.name}>{facet.name}</option>)}</SelectField><Field label="Minimum price" type="number" min="0" step="any" placeholder="No minimum" value={draft.minPrice} onChange={event => setDraft({ ...draft, minPrice: event.target.value })} /><Field label="Maximum price" type="number" min="0" step="any" placeholder="No maximum" value={draft.maxPrice} onChange={event => setDraft({ ...draft, maxPrice: event.target.value })} error={badPriceRange ? "Enter a maximum above the minimum." : undefined} /></div>
        <div className="shop-filter-panel-actions"><button type="button" className="shop-text-button" onClick={clearFilters}>Reset filters</button><Button type="submit" disabled={badPriceRange}>Apply filters <ArrowRight size={16} aria-hidden="true" /></Button></div>
      </form> : null}
      <div className="shop-category-tabs" aria-label="Filter by category"><button type="button" className={!activeCategory ? "is-active" : ""} aria-pressed={!activeCategory} onClick={() => selectCategory("")}>All finds</button>{categoryFacets.map(facet => <button key={facet.name} type="button" className={activeCategory === facet.name ? "is-active" : ""} aria-pressed={activeCategory === facet.name} onClick={() => selectCategory(facet.name)}>{facet.name}</button>)}</div>
      {hasFilters ? <div className="shop-applied-filters">{appliedFilters.map(key => <button type="button" key={key} onClick={() => removeFilter(key)} aria-label={`Remove ${key} filter: ${params.get(key)}`}><span>{key === "minPrice" ? "Min: " : key === "maxPrice" ? "Max: " : key === "q" ? "Search: " : ""}{params.get(key)}</span><X size={13} aria-hidden="true" /></button>)}<button className="shop-clear-all" type="button" onClick={clearFilters}>Clear all</button></div> : null}

      {error ? <Alert tone="danger" title="We couldn’t load the collection" action={<Button variant="secondary" onClick={retry}>Try again</Button>}>{error}</Alert> : null}
      <div className="shop-product-grid" aria-busy={loading || loadingMore}>
        {loading ? <ProductSkeletons /> : products.map(product => <ShopProductCard key={product.id} product={product} />)}
        {loadingMore ? <ProductSkeletons count={4} /> : null}
      </div>
      {!loading && !error && products.length === 0 ? <EmptyState title="Nothing here just yet." message="Try another search or loosen your filters. Your next good find could be one click away." action={<Button onClick={clearFilters}>Explore all products</Button>} /> : null}
      <div className="shop-load-zone" ref={sentinelRef}>
        {loadingMore ? <p className="shop-loading-more" role="status"><LoaderCircle size={20} className="shop-spin" aria-hidden="true" />Finding a little more good…</p> : null}
        {loadMoreError ? <Alert tone="warning" title="Your finds are still here" action={<Button variant="secondary" onClick={retry}>Retry loading</Button>}>We couldn’t load the next products. Please try again.</Alert> : null}
        {!loading && !error && products.length > 0 ? <>
          <span className="shop-explored-count" role="status">You’ve explored {products.length} of {totalElements} products</span>
          <progress className="shop-progress" value={products.length} max={Math.max(totalElements, products.length, 1)} aria-label="Products explored" />
          {hasMore ? <><p className="shop-scroll-hint">More finds appear as you scroll.</p><button className="shop-load-button" type="button" disabled={loadingMore} onClick={loadMore}>Load more products <ArrowDown size={16} aria-hidden="true" /></button></> : <><p className="shop-end-message">That’s the whole collection. Found your favorite?</p><button className="shop-text-button" type="button" onClick={focusCollection}>Back to the collection <ArrowUp size={15} aria-hidden="true" /></button></>}
        </> : null}
      </div>
    </section>
  </div>;
}
