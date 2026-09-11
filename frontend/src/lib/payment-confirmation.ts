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
