import { useCallback } from "react";
import type { Product } from "../domain";
import { api } from "./api";
import { toPage } from "./format";
import { useInfiniteProducts } from "./useInfiniteProducts";

/** Keep authenticated seller records separate from the public catalogue stream. */
export function useSellerProducts(accessToken: string | null) {
  const fetchPage = useCallback(async (query: string) => {
    if (!accessToken) throw new Error("Your session has ended. Please sign in again.");
    return toPage<Product>(await api.products.sellerList(accessToken, query));
  }, [accessToken]);
  return useInfiniteProducts("", fetchPage);
}
