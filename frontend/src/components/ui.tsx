import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { CheckCircle2, ChevronLeft, ChevronRight, CircleAlert, Info, LoaderCircle, PackageOpen, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import type { Page, Product } from "../domain";

export function Button({ className = "", variant = "primary", loading, children, disabled, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ghost" | "danger"; loading?: boolean }) {
  return <button className={`button button-${variant} inline-flex items-center justify-center gap-2 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-brand-500/20 ${className}`} disabled={disabled || loading} {...props}>{loading ? <span className="spinner" aria-label="Loading" /> : null}{children}</button>;
}

export function Field({ label, hint, error, className = "", ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  return <label className={`field ${className}`}>
    <span className="field-label">{label}</span>
    <input className={error ? "input input-error" : "input"} {...props} />
    {hint ? <span className="field-hint">{hint}</span> : null}
    {error ? <span className="field-error" role="alert">{error}</span> : null}
  </label>;
}

export function TextArea({ label, hint, error, className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: string; error?: string }) {
  return <label className={`field ${className}`}>
    <span className="field-label">{label}</span>
    <textarea className={error ? "input textarea input-error" : "input textarea"} {...props} />
    {hint ? <span className="field-hint">{hint}</span> : null}
    {error ? <span className="field-error" role="alert">{error}</span> : null}
  </label>;
}

export function SelectField({ label, error, children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string; children: ReactNode }) {
  return <label className="field"><span className="field-label">{label}</span><select className={error ? "input input-error" : "input"} {...props}>{children}</select>{error ? <span className="field-error" role="alert">{error}</span> : null}</label>;
}

export function Alert({ tone = "info", title, children, action }: { tone?: "info" | "success" | "warning" | "danger"; title?: string; children: ReactNode; action?: ReactNode }) {
  const icon = tone === "success" ? <CheckCircle2 size={18} aria-hidden="true" /> : tone === "warning" ? <TriangleAlert size={18} aria-hidden="true" /> : tone === "danger" ? <CircleAlert size={18} aria-hidden="true" /> : <Info size={18} aria-hidden="true" />;
  return <div className={`alert alert-${tone}`} role={tone === "danger" ? "alert" : "status"}>
    <span className="alert-icon">{icon}</span><div className="alert-content"><strong>{title}</strong>{title ? <span> — </span> : null}{children}</div>{action}
  </div>;
}

export function StatusBadge({ value }: { value?: string | null }) {
  const normalized = (value ?? "unknown").toLowerCase().replace(/_/g, "-");
  const tone = /success|confirmed|active|completed/.test(normalized) ? "success" : /failed|cancelled|suspended|deleted|refund-failed/.test(normalized) ? "danger" : /pending|processing|requires|requested|review/.test(normalized) ? "warning" : "neutral";
  return <span className={`status status-${tone}`}><span className="status-dot" aria-hidden="true" />{value?.replace(/_/g, " ") ?? "Unknown"}</span>;
}

export function ProductImage({ product, compact = false }: { product: Product; compact?: boolean }) {
  const src = product.imageUrls?.find(Boolean);
  if (src) return <img className={compact ? "product-image product-image-small" : "product-image"} src={src} alt={product.name} />;
  const initial = product.name.slice(0, 1).toUpperCase();
  return <div className={compact ? "product-image product-image-small product-placeholder" : "product-image product-placeholder"} aria-label={`${product.name} image unavailable`} role="img"><span>{initial}</span></div>;
}

export function LoadingBlock({ label = "Loading" }: { label?: string }) {
  return <div className="loading-block" role="status"><span className="loading-icon"><LoaderCircle size={21} aria-hidden="true" /></span><span>{label}</span></div>;
}

export function EmptyState({ title, message, action }: { title: string; message: string; action?: ReactNode }) {
  return <div className="empty-state"><div className="empty-icon"><PackageOpen size={27} aria-hidden="true" /></div><h2>{title}</h2><p>{message}</p>{action}</div>;
}

export function PageError({ message, retry }: { message: string; retry?: () => void }) {
  return <Alert tone="danger" title="Unable to load this page" action={retry ? <Button variant="secondary" onClick={retry}>Try again</Button> : undefined}>{message}</Alert>;
}

export function Pagination<T>({ page, onPage }: { page: Page<T>; onPage: (next: number) => void }) {
  if (page.totalPages <= 1) return null;
  return <nav className="pagination" aria-label="Pagination"><Button variant="secondary" disabled={page.number <= 0} onClick={() => onPage(page.number - 1)}><ChevronLeft size={16} aria-hidden="true" />Previous</Button><span className="pagination-position">Page <strong>{page.number + 1}</strong> of {page.totalPages}</span><Button variant="secondary" disabled={page.number + 1 >= page.totalPages} onClick={() => onPage(page.number + 1)}>Next<ChevronRight size={16} aria-hidden="true" /></Button></nav>;
}

export function SafeLink({ to, children, className = "" }: { to: string; children: ReactNode; className?: string }) {
  return <Link className={`link ${className}`} to={to}>{children}</Link>;
}
