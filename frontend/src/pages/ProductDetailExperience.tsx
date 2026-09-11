import { useState } from "react";
import { ArrowLeft, ArrowRight, Check, ChevronDown, ChevronRight, Minus, Plus, ShoppingBag } from "lucide-react";
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
  if (error || !product) {
    return <section className="pd-experience"><EmptyState title="We couldn't find this product" message="It may no longer be available in the collection." action={<div className="pd-empty-actions"><Link className="button button-primary" to="/">Explore the collection</Link>{error ? <Button variant="secondary" onClick={() => void reload()}>Try again</Button> : null}</div>} /></section>;
  }
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
    setAdding(true);
    setError(null);
    setAddedQuantity(null);
    try {
      await add(product.id, quantity);
      setAddedQuantity(quantity);
    } catch (caught) {
      setError(messageForError(caught));
    } finally {
      setAdding(false);
    }
  }

  return (
    <section className="pd-experience" aria-labelledby="pd-product-title">
      <nav className="pd-breadcrumb" aria-label="Breadcrumb">
        <Link to="/">Home</Link>
        <ChevronRight size={13} aria-hidden="true" />
        <Link to={categoryUrl}>{product.category || "Collection"}</Link>
        <ChevronRight size={13} aria-hidden="true" />
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="pd-layout">
        <div className="pd-gallery">
          <div className="pd-main-image" id="pd-gallery-image">
            <ProductImage key={activeImage || product.id} product={{ ...product, imageUrls: activeImage ? [activeImage] : [] }} />
            <Link className="pd-gallery-back" to={categoryUrl} aria-label="Back to the collection"><ArrowLeft size={18} aria-hidden="true" /></Link>
            {images.length > 1 ? <span className="pd-image-count" aria-live="polite">{selectedImage + 1} / {images.length}</span> : null}
          </div>
          {images.length > 1 ? (
            <div className="pd-thumbnails" role="group" aria-label="Product photos">
              {images.map((url, index) => (
                <button key={url} type="button" className={`pd-thumbnail${selectedImage === index ? " is-selected" : ""}`} aria-label={`Show photo ${index + 1} of ${product.name}`} aria-pressed={selectedImage === index} aria-controls="pd-gallery-image" onClick={() => setSelectedImage(index)}>
                  <ProductImage product={{ ...product, imageUrls: [url] }} compact />
                </button>
              ))}
            </div>
          ) : null}
          <div className="pd-gallery-caption"><span>Pepekart collection</span><span>{product.category || "Everyday finds"}</span></div>
        </div>

        <div className="pd-information">
          <div className="pd-product-label"><span className="pd-label-dot" aria-hidden="true" />{product.brand || "The Pepekart edit"}<span className="pd-label-divider" aria-hidden="true">/</span><Link to={categoryUrl}>{product.category || "Collection"}</Link></div>
          <h1 id="pd-product-title">{product.name}</h1>
          <div className="pd-price-line"><strong>{formatMoney(product.price, product.currency)}</strong><span>per item</span></div>
          <p className="pd-description">{product.description || "Explore this find from the Pepekart collection."}</p>

          <div className="pd-purchase">
            <div className="pd-quantity-heading"><label id="pd-quantity-label">Choose your quantity</label><span>Up to 100 per order</span></div>
            <div className="pd-purchase-row">
              <div className="pd-quantity" role="group" aria-labelledby="pd-quantity-label">
                <button type="button" aria-label="Reduce quantity" disabled={adding || unavailable || quantity <= 1} onClick={() => setQuantity((current) => Math.max(1, current - 1))}><Minus size={16} aria-hidden="true" /></button>
                <output aria-label="Quantity" aria-live="polite">{quantity}</output>
                <button type="button" aria-label="Increase quantity" disabled={adding || unavailable || quantity >= 100} onClick={() => setQuantity((current) => Math.min(100, current + 1))}><Plus size={16} aria-hidden="true" /></button>
              </div>
              <Button className="pd-add-button" loading={adding} disabled={unavailable} onClick={() => void addToCart()}>
                {!adding ? <ShoppingBag size={19} aria-hidden="true" /> : null}
                <span>{unavailable ? "Currently unavailable" : adding ? "Adding to your bag" : "Add to bag"}</span>
                {!adding && !unavailable ? <ArrowRight size={18} aria-hidden="true" /> : null}
              </Button>
            </div>
            {addedQuantity !== null ? <div className="pd-added" role="status"><Check size={18} aria-hidden="true" /><span>{addedQuantity} {addedQuantity === 1 ? "item" : "items"} added to your bag.</span><Link to="/cart">View bag <ArrowRight size={15} aria-hidden="true" /></Link></div> : null}
            {error ? <Alert tone="danger">{error}</Alert> : null}
            <p className="pd-checkout-note">Final price and availability are confirmed at checkout.</p>
          </div>

          <div className="pd-details">
            <details open>
              <summary>Product details <ChevronDown size={17} aria-hidden="true" /></summary>
              <dl>
                {product.brand ? <div><dt>Brand</dt><dd>{product.brand}</dd></div> : null}
                <div><dt>Category</dt><dd>{product.category || "General"}</dd></div>
                <div><dt>Currency</dt><dd>{product.currency || "INR"}</dd></div>
              </dl>
            </details>
            <details>
              <summary>Before you order <ChevronDown size={17} aria-hidden="true" /></summary>
              <p>Review your selected items and quantities in your bag. You can make changes there before continuing to checkout.</p>
            </details>
          </div>
          <Link className="pd-continue" to={categoryUrl}>More in {product.category || "the collection"}<ArrowRight size={17} aria-hidden="true" /></Link>
        </div>
      </div>
    </section>
  );
}
