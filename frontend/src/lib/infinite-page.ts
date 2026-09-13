import type { Page } from "../domain";

export interface InfinitePageState<T> {
  items: T[];
  totalElements: number;
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  loadMoreError: string | null;
  hasMore: boolean;
}

function initialState<T>(): InfinitePageState<T> {
  return { items: [], totalElements: 0, loading: true, loadingMore: false, error: null, loadMoreError: null, hasMore: true };
}

/** Sequential append-only loading. A refresh invalidates every older request. */
export class InfinitePageController<T extends { id: string }> {
  private readonly fetchPage: (page: number) => Promise<Page<T>>;
  private state = initialState<T>();
  private readonly listeners = new Set<() => void>();
  private generation = 0;
  private nextPage = 0;
  private active = false;
  private inFlight = false;

  constructor(fetchPage: (page: number) => Promise<Page<T>>) { this.fetchPage = fetchPage; }

  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  start = () => {
    this.generation += 1;
    this.nextPage = 0;
    this.active = true;
    this.inFlight = false;
    this.update(initialState<T>());
    return this.requestNext();
  };

  dispose = () => {
    this.active = false;
    this.generation += 1;
    this.inFlight = false;
  };

  loadMore = () => this.requestNext();
  retry = () => this.requestNext();
  autoLoadMore = () => {
    if (this.state.error || this.state.loadMoreError) return Promise.resolve();
    return this.requestNext();
  };

  private update(state: InfinitePageState<T>) {
    this.state = state;
    this.listeners.forEach(listener => listener());
  }

  private async requestNext() {
    if (!this.active || this.inFlight || !this.state.hasMore) return;
    this.inFlight = true;
    const generation = this.generation;
    const requestedPage = this.nextPage;
    this.update({ ...this.state, loading: requestedPage === 0, loadingMore: requestedPage > 0, error: null, loadMoreError: null });
    try {
      const page = await this.fetchPage(requestedPage);
      if (!this.active || generation !== this.generation) return;
      const seen = new Set(this.state.items.map(item => item.id));
      const items = [...this.state.items];
      for (const item of page.content) {
        if (seen.has(item.id)) continue;
        seen.add(item.id);
        items.push(item);
      }
      this.nextPage = requestedPage + 1;
      this.inFlight = false;
      this.update({ items, totalElements: page.totalElements, loading: false, loadingMore: false, error: null, loadMoreError: null,
        hasMore: page.content.length > 0 && !page.last && this.nextPage < page.totalPages });
    } catch (caught) {
      if (!this.active || generation !== this.generation) return;
      this.inFlight = false;
      const message = caught instanceof Error ? caught.message : "Unable to load records. Please try again.";
      this.update({ ...this.state, loading: false, loadingMore: false,
        error: requestedPage === 0 ? message : null, loadMoreError: requestedPage > 0 ? message : null });
    }
  }
}
