import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import type { Page, Product } from "../domain";
import { api } from "./api";
import { toPage } from "./format";
import { InfiniteProductsController } from "./infinite-products";

const fetchProductPage = (query: string) => api.products.list(query).then(toPage<Product>);

/** Filters/sorting identify a fresh stream; pagination is owned by this hook. */
export function useInfiniteProducts(query: string, fetchPage: (query: string) => Promise<Page<Product>> = fetchProductPage) {
  const controller = useMemo(() => new InfiniteProductsController(query, fetchPage), [query, fetchPage]);
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot, controller.getSnapshot);
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void controller.start();
    return controller.dispose;
  }, [controller]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || typeof IntersectionObserver === "undefined" || state.loading || state.loadingMore
      || !state.hasMore || state.error || state.loadMoreError) return;

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void controller.autoLoadMore();
    }, { rootMargin: "500px 0px", threshold: 0 });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [controller, state.loading, state.loadingMore, state.hasMore, state.error, state.loadMoreError, state.products.length]);

  return { ...state, loadMore: controller.loadMore, retry: controller.retry, reload: () => controller.start(), sentinelRef };
}
