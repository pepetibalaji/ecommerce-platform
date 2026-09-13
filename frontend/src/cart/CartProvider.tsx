import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore, type PropsWithChildren } from "react";
import { useAuth } from "../auth/AuthProvider";
import { api } from "../lib/api";
import { createIdempotencyKey } from "../lib/format";
import { CartRequestController, type CartState } from "./cart-requests";

interface CartContextValue extends Omit<CartState, "ownerKey"> {
  refresh: () => Promise<void>;
  add: (productId: string, quantity?: number) => Promise<void>;
  update: (itemId: string, quantity: number) => Promise<void>;
  remove: (itemId: string) => Promise<void>;
  mergeGuest: () => Promise<void>;
}

const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: PropsWithChildren) {
  const { user, accessToken, isAuthenticated, isLoading: authLoading } = useAuth();
  const [requests] = useState(() => new CartRequestController(api.cart, createIdempotencyKey));
  const state = useSyncExternalStore(requests.subscribe, requests.getSnapshot, requests.getSnapshot);
  const ownerKey = authLoading ? null : isAuthenticated && user ? `customer:${user.id}` : "guest";
  const token = isAuthenticated ? accessToken : null;

  useEffect(() => {
    void requests.setSession(ownerKey ? { key: ownerKey, accessToken: token } : null);
  }, [ownerKey, requests, token]);

  const value = useMemo<CartContextValue>(() => {
    // Hide the previous owner's cart before the effect starts its replacement.
    const current = state.ownerKey === ownerKey;
    return {
      cart: current ? state.cart : null,
      isLoading: authLoading || !current || state.isLoading,
      error: current ? state.error : null,
      mergeState: current ? state.mergeState : "idle",
      refresh: requests.refresh, add: requests.add, update: requests.update, remove: requests.remove, mergeGuest: requests.mergeGuest,
    };
  }, [authLoading, ownerKey, requests, state]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error("useCart must be used within CartProvider");
  return context;
}
