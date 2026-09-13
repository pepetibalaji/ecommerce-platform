import { ApiError, type Cart } from "../domain";

export interface CartSession {
  key: string;
  accessToken: string | null;
}

export interface CartState {
  ownerKey: string | null;
  cart: Cart | null;
  isLoading: boolean;
  error: string | null;
  mergeState: "idle" | "merging" | "complete" | "retry";
}

interface CartRequests {
  guest: () => Promise<Cart>;
  customer: (token: string) => Promise<Cart>;
  mergeGuest: (token: string, key: string) => Promise<Cart>;
  addGuest: (productId: string, quantity: number, key: string) => Promise<Cart>;
  addCustomer: (token: string, productId: string, quantity: number, key: string) => Promise<Cart>;
  updateGuest: (itemId: string, quantity: number) => Promise<Cart>;
  updateCustomer: (token: string, itemId: string, quantity: number) => Promise<Cart>;
  removeGuest: (itemId: string) => Promise<Cart>;
  removeCustomer: (token: string, itemId: string) => Promise<Cart>;
}

type Wait = (milliseconds: number) => Promise<void>;
const wait: Wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const initialState = (ownerKey: string | null): CartState => ({ ownerKey, cart: null, isLoading: true, error: null, mergeState: "idle" });

/** Used only for reads and mutations protected by a stable idempotency key. */
export async function retryCartLockContention<T>(operation: () => Promise<T>, pause: Wait = wait, isCurrent = () => true): Promise<T | undefined> {
  for (let attempt = 0; attempt < 3 && isCurrent(); attempt += 1) {
    try { return await operation(); }
    catch (error) {
      if (!isCurrent()) return;
      if (!(error instanceof ApiError) || error.status !== 409 || error.code !== "CART_LOCK_CONTENTION" || attempt === 2) throw error;
      await pause(Math.min(Math.max(error.retryAfter ?? 1, 0) * 1000, 3000));
    }
  }
}

/** Keeps one browser's cart requests ordered and discards work from an earlier account. */
export class CartRequestController {
  private state = initialState(null);
  private session: CartSession | null = null;
  private generation = 0;
  private readonly listeners = new Set<() => void>();
  private readonly requests: CartRequests;
  private readonly createKey: () => string;
  private readonly pause: Wait;
  private tail = Promise.resolve();
  private refreshTask: Promise<void> | null = null;
  private mergeTask: Promise<void> | null = null;
  private mergeKey: string | null = null;

  constructor(requests: CartRequests, createKey: () => string, pause: Wait = wait) {
    this.requests = requests;
    this.createKey = createKey;
    this.pause = pause;
  }

  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  setSession = (session: CartSession | null): Promise<void> => {
    if (session?.key === this.session?.key) {
      this.session = session;
      return this.mergeTask ?? this.refreshTask ?? Promise.resolve();
    }
    this.session = session;
    this.generation += 1;
    this.tail = Promise.resolve();
    this.refreshTask = null;
    this.mergeTask = null;
    this.mergeKey = null;
    this.publish(initialState(session?.key ?? null));
    // Finish the merge before reading the customer cart.
    return session ? session.accessToken ? this.mergeGuest() : this.refresh() : Promise.resolve();
  };

  refresh = (): Promise<void> => {
    if (!this.session) return Promise.resolve();
    if (this.mergeTask) return this.mergeTask;
    if (this.refreshTask) return this.refreshTask;
    const task = this.enqueue((session, isCurrent) => this.read(session, isCurrent), "refresh").finally(() => {
      if (this.refreshTask === task) this.refreshTask = null;
    });
    this.refreshTask = task;
    return task;
  };

  mergeGuest = (): Promise<void> => {
    if (!this.session?.accessToken) return Promise.resolve();
    if (this.mergeTask) return this.mergeTask;
    const key = this.mergeKey ?? this.createKey();
    this.mergeKey = key;
    const task = this.enqueue(async (session, isCurrent) => {
      let mergeFailed = false;
      try { await retryCartLockContention(() => this.requests.mergeGuest(session.accessToken!, key), this.pause, isCurrent); }
      catch (error) {
        if (!isCurrent()) return;
        // The service returns 400 when this browser has no guest cookie.
        mergeFailed = !(error instanceof ApiError && error.status === 400);
      }
      if (!isCurrent()) return;
      // An idempotent merge replay may contain an older snapshot than later cart edits.
      const cart = await this.read(session, isCurrent);
      if (isCurrent() && mergeFailed) this.publish({ ...this.state, mergeState: "retry" });
      return cart;
    }, "merge").finally(() => {
      if (this.mergeTask === task) this.mergeTask = null;
    });
    this.mergeTask = task;
    return task;
  };

  add = (productId: string, quantity = 1): Promise<void> => {
    const key = this.createKey();
    return this.enqueue((session, isCurrent) => retryCartLockContention(() => session.accessToken
      ? this.requests.addCustomer(session.accessToken, productId, quantity, key)
      : this.requests.addGuest(productId, quantity, key), this.pause, isCurrent), "mutation");
  };

  update = (itemId: string, quantity: number): Promise<void> => this.enqueue(session => session.accessToken
    ? this.requests.updateCustomer(session.accessToken, itemId, quantity)
    : this.requests.updateGuest(itemId, quantity), "mutation");

  remove = (itemId: string): Promise<void> => this.enqueue(session => session.accessToken
    ? this.requests.removeCustomer(session.accessToken, itemId)
    : this.requests.removeGuest(itemId), "mutation");

  private read(session: CartSession, isCurrent: () => boolean) {
    return retryCartLockContention(() => session.accessToken ? this.requests.customer(session.accessToken) : this.requests.guest(), this.pause, isCurrent);
  }

  private publish(state: CartState) {
    this.state = state;
    this.listeners.forEach(listener => listener());
  }

  private enqueue(operation: (session: CartSession, isCurrent: () => boolean) => Promise<Cart | undefined>, mode: "refresh" | "merge" | "mutation"): Promise<void> {
    if (!this.session) return mode === "mutation" ? Promise.reject(new Error("Your session is still loading. Please try again.")) : Promise.resolve();
    const generation = this.generation;
    const isCurrent = () => generation === this.generation;
    const task = this.tail.then(async () => {
      if (!isCurrent() || !this.session) return;
      if (mode !== "mutation") this.publish({ ...this.state, isLoading: true, error: null, mergeState: mode === "merge" ? "merging" : this.state.mergeState });
      try {
        const cart = await operation(this.session, isCurrent);
        if (isCurrent() && cart) this.publish({ ...this.state, cart, error: null, mergeState: mode === "merge" && this.state.mergeState !== "retry" ? "complete" : this.state.mergeState });
      } catch (error) {
        if (!isCurrent()) return;
        if (mode === "mutation") throw error;
        this.publish({ ...this.state, error: error instanceof Error ? error.message : "We could not load your cart.", mergeState: mode === "merge" ? "retry" : this.state.mergeState });
      } finally {
        if (isCurrent() && mode !== "mutation") this.publish({ ...this.state, isLoading: false });
      }
    });
    this.tail = task.catch(() => undefined);
    return task;
  }
}
