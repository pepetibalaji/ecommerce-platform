import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import type { Page } from "../domain";
import { InfinitePageController } from "./infinite-page";

/** Pass a memoized fetcher: changing its filters or identity starts a fresh list. */
export function useInfinitePage<T extends { id: string }>(fetchPage: (page: number) => Promise<Page<T>>) {
  const controller = useMemo(() => new InfinitePageController(fetchPage), [fetchPage]);
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
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void controller.autoLoadMore();
    }, { rootMargin: "160px 0px", threshold: 0 });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [controller, state.loading, state.loadingMore, state.hasMore, state.error, state.loadMoreError, state.items.length]);

  return { ...state, reload: controller.start, retry: controller.retry, loadMore: controller.loadMore, sentinelRef };
}
