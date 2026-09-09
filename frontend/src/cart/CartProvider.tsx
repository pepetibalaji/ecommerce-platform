import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from "react";
import { useAuth } from "../auth/AuthProvider";
import { ApiError, type Cart } from "../domain";
import { api } from "../lib/api";
import { createIdempotencyKey } from "../lib/format";

interface CartContextValue {
  cart: Cart | null;
  isLoading: boolean;
  error: string | null;
  mergeState: "idle" | "merging" | "complete" | "retry";
  refresh: () => Promise<void>;
  add: (productId: string, quantity?: number) => Promise<void>;
  update: (itemId: string, quantity: number) => Promise<void>;
  remove: (itemId: string) => Promise<void>;
  mergeGuest: () => Promise<void>;
}

const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: PropsWithChildren) {
  const { accessToken, isAuthenticated } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [mergeState, setMergeState] = useState<CartContextValue["mergeState"]>("idle");
  const mergeIdempotencyKey = useRef<string | null>(null);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const value = isAuthenticated && accessToken ? await api.cart.customer(accessToken) : await api.cart.guest();
      setCart(value);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "We could not load your cart.");
    } finally {
      setIsLoading(false);
    }
  }, [accessToken, isAuthenticated]);

  const mergeGuest = useCallback(async () => {
    if (!accessToken) return;
    setMergeState("merging");
    setError(null);
    const key = mergeIdempotencyKey.current ?? createIdempotencyKey();
    mergeIdempotencyKey.current = key;
    try {
      setCart(await api.cart.mergeGuest(accessToken, key));
      setMergeState("complete");
    } catch (caught) {
      // A signed-in visitor who never had a guest cookie receives the current
      // Cart Service's generic 400. Treat that as an empty/no-guest merge and
      // load the authoritative customer cart instead of showing a false failure.
      if (caught instanceof ApiError && caught.status === 400) {
        try {
          setCart(await api.cart.customer(accessToken));
          setMergeState("complete");
          return;
        } catch (refreshError) {
          setMergeState("retry");
          setError(refreshError instanceof Error ? refreshError.message : "Your cart could not be loaded yet.");
          return;
        }
      }
      setMergeState("retry");
      setError(caught instanceof Error ? caught.message : "Your guest cart could not be merged yet.");
    }
  }, [accessToken]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (isAuthenticated && accessToken && mergeState === "idle") void mergeGuest();
  }, [accessToken, isAuthenticated, mergeGuest, mergeState]);

  useEffect(() => {
    if (!isAuthenticated) {
      mergeIdempotencyKey.current = null;
      setMergeState("idle");
    }
  }, [isAuthenticated]);

  const add = useCallback(async (productId: string, quantity = 1) => {
    const key = createIdempotencyKey();
    const next = isAuthenticated && accessToken
      ? await api.cart.addCustomer(accessToken, productId, quantity, key)
      : await api.cart.addGuest(productId, quantity, key);
    setCart(next);
  }, [accessToken, isAuthenticated]);

  const update = useCallback(async (itemId: string, quantity: number) => {
    const next = isAuthenticated && accessToken
      ? await api.cart.updateCustomer(accessToken, itemId, quantity)
      : await api.cart.updateGuest(itemId, quantity);
    setCart(next);
  }, [accessToken, isAuthenticated]);

  const remove = useCallback(async (itemId: string) => {
    const next = isAuthenticated && accessToken
      ? await api.cart.removeCustomer(accessToken, itemId)
      : await api.cart.removeGuest(itemId);
    setCart(next);
  }, [accessToken, isAuthenticated]);

  const value = useMemo(() => ({ cart, isLoading, error, mergeState, refresh, add, update, remove, mergeGuest }), [add, cart, error, isLoading, mergeGuest, mergeState, refresh, remove, update]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error("useCart must be used within CartProvider");
  return context;
}
