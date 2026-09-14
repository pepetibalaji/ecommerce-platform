/** Bounded status checks; retries never create a new checkout or charge. */
export async function pollPaymentConfirmation(
  check: () => Promise<{ terminal: boolean } | null>,
  cancelled: () => boolean,
  progress: (attempt: number) => void,
  exhausted: () => void,
  pause: (ms: number) => Promise<void> = ms => new Promise(resolve => setTimeout(resolve, ms)),
) {
  for (let attempt = 0; attempt < 25; attempt += 1) {
    if (cancelled()) return;
    const result = await check();
    if (cancelled() || !result || result.terminal) return;
    progress(attempt + 1);
    if (attempt < 24) await pause(attempt === 0 ? 2000 : 5000);
  }
  if (!cancelled()) exhausted();
}

/** Only the exact production provider host is eligible; suffix matches are unsafe. */
export function approvedCheckoutUrl(url: string, expiresAt: string | undefined,
  options: { now?: number; developmentOrigin?: string; sandbox?: boolean } = {}) {
  const parsed = new URL(url);
  if (parsed.username || parsed.password) throw new Error("Invalid checkout URL");
  const production = parsed.protocol === "https:" && parsed.hostname === "checkout.stripe.com" && !parsed.port;
  const development = options.developmentOrigin && parsed.origin === options.developmentOrigin && parsed.pathname === "/payment/return";
  const sandbox = options.sandbox === true && parsed.origin === "http://localhost:3001" && parsed.pathname === "/mock-checkout";
  if (!production && !development && !sandbox) throw new Error("Invalid checkout host");
  if (!expiresAt || !/(Z|[+-]\d{2}:\d{2})$/.test(expiresAt)
      || !Number.isFinite(Date.parse(expiresAt)) || Date.parse(expiresAt) <= (options.now ?? Date.now())) {
    throw new Error("Checkout session expired");
  }
  return parsed.toString();
}

export function paymentPreparationDelay(error: { code?: string; retryable?: boolean; retryAfter?: number }, attempt: number) {
  if (error.code !== "PAYMENT_PREPARING" || !error.retryable || attempt >= 4) return null;
  return Math.min(5, Math.max(1, error.retryAfter ?? 2)) * 1000;
}
