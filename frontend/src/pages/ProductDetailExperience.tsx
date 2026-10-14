import { useState } from "react";
import { ArrowLeft, ArrowRight, Check, ChevronDown, ChevronRight, Layers3, Minus, Plus, ShieldCheck, ShoppingBag } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { useCart } from "../cart/CartProvider";
import { Alert, Button, EmptyState, LoadingBlock, ProductImage } from "../components/ui";
import type { Product } from "../domain";
import { api } from "../lib/api";
import { formatMoney, messageForError } from "../lib/format";
import { useResource } from "../lib/hooks";
import "../styles/product-detail.css";

export function ProductDetailPage() {
  const { productId = "" } = useParams();
  const { data: product, loading, error, reload } = useResource(() => api.products.byId(productId), [productId]);
  if (loading) return <section className="pd-experience"><LoadingBlock label="Finding your next favourite" /></section>;
  if (error || !product) return <section className="pd-experience"><EmptyState title="We couldn't find this product" message="It may no longer be available in the collection." action={<div className="pd-empty-actions"><Link className="button button-primary" to="/">Explore the collection</Link>{error ? <Button variant="secondary" onClick={() => void reload()}>Try again</Button> : null}</div>} /></section>;
  return <ProductExperience key={product.id} product={product} />;
}

function ProductExperience({ product }: { product: Product }) {
  const { add } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [addedQuantity, setAddedQuantity] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedImage, setSelectedImage] = useState(0);
  const images = [...new Set(product.imageUrls?.filter(Boolean) ?? [])];
  const activeImage = images[selectedImage] ?? images[0];
  const categoryUrl = product.category ? `/?category=${encodeURIComponent(product.category)}#catalogue` : "/#catalogue";
  const unavailable = product.active === false;

  async function addToCart() {
    if (adding || unavailable || quantity < 1 || quantity > 100) return;
    setAdding(true); setError(null); setAddedQuantity(null);
    try { await add(product.id, quantity); setAddedQuantity(quantity); }
    catch (caught) { setError(messageForError(caught)); }
    finally { setAdding(false); }
  }

  return <section className="pd-experience" aria-labelledby="pd-product-title">
    <header className="pd-editorial-header">
      <nav className="pd-breadcrumb" aria-label="Breadcrumb"><Link to="/">The store</Link><ChevronRight size={12} aria-hidden="true" /><Link to={categoryUrl}>{product.category || "Collection"}</Link><ChevronRight size={12} aria-hidden="true" /><span aria-current="page">{product.name}</span></nav>
      <div className="pd-editorial-title"><div><span className="pd-kicker">The everyday collection / {product.category || "Good finds"}</span><h1 id="pd-product-title">{product.name}</h1></div><Link className="pd-category-nav" to={categoryUrl}><ArrowLeft size={16} aria-hidden="true" /><span>Back to<br /><strong>{product.category || "the collection"}</strong></span></Link></div>
    </header>

    <div className="pd-layout">
      <div className="pd-editorial-content">
        <section className="pd-gallery" aria-label="Product gallery">
          <div className="pd-gallery-topline"><span><Layers3 size={15} aria-hidden="true" />A closer look</span><span>{images.length ? `${String(selectedImage + 1).padStart(2, "0")} / ${String(images.length).padStart(2, "0")}` : "Product preview"}</span></div>
          <div className="pd-main-image" id="pd-gallery-image"><ProductImage key={activeImage || product.id} product={{ ...product, imageUrls: activeImage ? [activeImage] : [] }} /><span className="pd-image-caption">{product.brand || "Pepekart"}<span>{product.category || "The everyday edit"}</span></span></div>
          {images.length > 1 ? <div className="pd-thumbnails" role="group" aria-label="Product photos">{images.map((url, index) => <button key={url} type="button" className={`pd-thumbnail${selectedImage === index ? " is-selected" : ""}`} aria-label={`Show photo ${index + 1} of ${product.name}`} aria-pressed={selectedImage === index} aria-controls="pd-gallery-image" onClick={() => setSelectedImage(index)}><ProductImage product={{ ...product, imageUrls: [url] }} compact /><span>View {String(index + 1).padStart(2, "0")}</span></button>)}</div> : null}
        </section>

        <section className="pd-editorial-details" aria-labelledby="pd-details-heading"><header><span className="pd-kicker">Good to know</span><h2 id="pd-details-heading">The details<br /> <em>make the difference.</em></h2></header><div className="pd-details"><details open><summary><span className="pd-spec-number">01</span><span>About this find</span><ChevronDown size={19} aria-hidden="true" /></summary><p className="pd-description">{product.description || "Explore this find from the Pepekart collection."}</p></details><details><summary><span className="pd-spec-number">02</span><span>Product particulars</span><ChevronDown size={19} aria-hidden="true" /></summary><dl>{product.brand ? <div><dt>Brand</dt><dd>{product.brand}</dd></div> : null}<div><dt>Collection</dt><dd>{product.category || "General"}</dd></div><div><dt>Currency</dt><dd>{product.currency || "INR"}</dd></div></dl></details><details><summary><span className="pd-spec-number">03</span><span>Before you order</span><ChevronDown size={19} aria-hidden="true" /></summary><p>Review your selected items and quantities in your bag. Final prices and availability are confirmed at checkout, before you continue to payment.</p></details></div></section>
      </div>

      <aside className="pd-information" aria-label="Choose quantity and add to bag">
        <div className="pd-rail-top"><span className="pd-kicker">Make it yours</span><ShoppingBag size={24} strokeWidth={1.4} aria-hidden="true" /></div>
        <div className="pd-product-label">{product.brand || "The Pepekart edit"}<span>{product.category || "Collection"}</span></div>
        <h2>{product.name}</h2><div className="pd-price-line"><strong>{formatMoney(product.price, product.currency)}</strong><span>per item</span></div>
        <div className="pd-purchase"><div className="pd-quantity-heading"><label id="pd-quantity-label">Quantity</label><span>Up to 100 per order</span></div><div className="pd-purchase-row"><div className="pd-quantity" role="group" aria-labelledby="pd-quantity-label"><button type="button" aria-label="Reduce quantity" disabled={adding || unavailable || quantity <= 1} onClick={() => setQuantity((current) => Math.max(1, current - 1))}><Minus size={17} aria-hidden="true" /></button><output aria-label="Quantity" aria-live="polite">{quantity}</output><button type="button" aria-label="Increase quantity" disabled={adding || unavailable || quantity >= 100} onClick={() => setQuantity((current) => Math.min(100, current + 1))}><Plus size={17} aria-hidden="true" /></button></div><div className="pd-selected-total"><span>Selected total</span><strong>{formatMoney(product.price * quantity, product.currency)}</strong></div></div><Button className="pd-add-button" loading={adding} disabled={unavailable} onClick={() => void addToCart()}><span>{unavailable ? "Currently unavailable" : adding ? "Adding to your bag" : "Add to bag"}</span>{!adding ? <ArrowRight size={20} aria-hidden="true" /> : null}</Button>{addedQuantity !== null ? <div className="pd-added" role="status"><Check size={18} aria-hidden="true" /><span>{addedQuantity} {addedQuantity === 1 ? "item" : "items"} added to your bag.</span><Link to="/cart">View bag <ArrowRight size={14} aria-hidden="true" /></Link></div> : null}{error ? <Alert tone="danger">{error}</Alert> : null}<p className="pd-checkout-note"><ShieldCheck size={16} aria-hidden="true" />Review your order at checkout. Availability and final prices are confirmed there.</p></div>
        <Link className="pd-rail-link" to="/cart">Take a look at your bag<ArrowRight size={15} aria-hidden="true" /></Link>
      </aside>
    </div>
    <nav className="pd-next-collection" aria-label="Explore the collection"><div><span className="pd-kicker">There is more to discover</span><h2>Keep your good<br /><em>finds coming.</em></h2></div><Link to={categoryUrl}><span>Explore {product.category || "the collection"}</span><ArrowRight size={28} aria-hidden="true" /></Link><Link className="pd-all-products" to="/#catalogue">All products<ArrowRight size={16} aria-hidden="true" /></Link></nav>
  </section>;
}
