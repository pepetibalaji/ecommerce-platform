import type { Page, Product } from "../domain";

export const PRODUCT_BATCH_SIZE = 12;

export interface InfiniteProductsState {
  products: Product[];
  totalElements: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  loadMoreError: string | null;
  hasMore: boolean;
}

function initialState(): InfiniteProductsState {
  return {
    products: [], totalElements: 0, loading: true, loadingMore: false,
    error: null, loadMoreError: null, hasMore: true,
  };
}

export function productPageQuery(query: string, page: number) {
  const params = new URLSearchParams(query);
  params.set("page", String(page));
  params.set("size", String(PRODUCT_BATCH_SIZE));
  return params.toString();
}

/** One sequential product stream. Superseded requests never update its state. */
export class InfiniteProductsController {
  private query: string;
  private readonly fetchPage: (query: string) => Promise<Page<Product>>;
  private state = initialState();
  private readonly listeners = new Set<() => void>();
  private generation = 0;
  private nextPage = 0;
  private active = false;
  private inFlight = false;

  constructor(query: string, fetchPage: (query: string) => Promise<Page<Product>>) {
    this.query = query;
    this.fetchPage = fetchPage;
  }

  getSnapshot = () => this.state;

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  start = (query = this.query) => {
    this.query = query;
    this.generation += 1;
    this.nextPage = 0;
    this.active = true;
    this.inFlight = false;
    this.update(initialState());
    return this.requestNext();
  };

  dispose = () => {
    this.active = false;
    this.generation += 1;
    this.inFlight = false;
  };

  loadMore = () => this.requestNext();
  retry = () => this.requestNext();

  // Only a deliberate button press retries a failed page, avoiding observer loops.
  autoLoadMore = () => {
    if (this.state.error || this.state.loadMoreError) return Promise.resolve();
    return this.requestNext();
  };

  private update(state: InfiniteProductsState) {
    this.state = state;
    this.listeners.forEach((listener) => listener());
  }

  private async requestNext() {
    if (!this.active || this.inFlight || !this.state.hasMore) return;
    this.inFlight = true;
    const generation = this.generation;
    const requestedPage = this.nextPage;
    this.update({
      ...this.state,
      loading: requestedPage === 0,
      loadingMore: requestedPage > 0,
      error: null,
      loadMoreError: null,
    });

    try {
      const page = await this.fetchPage(productPageQuery(this.query, requestedPage));
      if (!this.active || generation !== this.generation) return;
      const seen = new Set(this.state.products.map((product) => product.id));
      const products = [...this.state.products];
      for (const product of page.content) {
        if (seen.has(product.id)) continue;
        seen.add(product.id);
        products.push(product);
      }
      this.nextPage = requestedPage + 1;
      this.inFlight = false;
      this.update({
        products,
        totalElements: page.totalElements,
        loading: false,
        loadingMore: false,
        error: null,
        loadMoreError: null,
        hasMore: page.content.length > 0 && !page.last && this.nextPage < page.totalPages,
      });
    } catch (caught) {
      if (!this.active || generation !== this.generation) return;
      this.inFlight = false;
      const message = caught instanceof Error ? caught.message : "We couldn't load these products. Please try again.";
      this.update({
        ...this.state,
        loading: false,
        loadingMore: false,
        error: requestedPage === 0 ? message : null,
        loadMoreError: requestedPage > 0 ? message : null,
      });
    }
  }
}
