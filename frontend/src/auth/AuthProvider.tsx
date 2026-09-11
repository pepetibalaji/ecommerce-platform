import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from "react";
import { api, configureSessionRecovery } from "../lib/api";
import { ApiError, type AuthSession, type Role, type User } from "../domain";

function positiveEnvMilliseconds(name: string, fallback: number) {
  const value = Number(import.meta.env[name]);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

const IDLE_TIMEOUT_MS = positiveEnvMilliseconds("VITE_SESSION_IDLE_TIMEOUT_MS", 30 * 60 * 1000);
const WARNING_WINDOW_MS = Math.min(positiveEnvMilliseconds("VITE_SESSION_WARNING_MS", 5 * 60 * 1000), Math.floor(IDLE_TIMEOUT_MS / 2));
const ACTIVE_REFRESH_INTERVAL_MS = Math.max(60_000, IDLE_TIMEOUT_MS - WARNING_WINDOW_MS - 5 * 60 * 1000);
const RECENT_ACTIVITY_WINDOW_MS = 5 * 60 * 1000;
const ACCESS_REFRESH_SKEW_MS = 60_000;

function normalizeUser(value: User): User {
  const roles: Role[] = value.roles?.length ? value.roles : value.role ? [value.role] : ["CUSTOMER"];
  return { ...value, role: value.role ?? roles[0], roles };
}

function displayRemaining(seconds: number) {
  return `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
}

export interface AuthContextValue {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<User>;
  refreshProfile: () => Promise<void>;
  logout: () => Promise<void>;
  hasAnyRole: (...roles: Role[]) => boolean;
  hasPermission: (permission: string) => boolean;
  consumeSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshInSeconds, setRefreshInSeconds] = useState<number | null>(null);
  const [idleDeadline, setIdleDeadline] = useState<number | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [showExpiryWarning, setShowExpiryWarning] = useState(false);
  const [continuingSession, setContinuingSession] = useState(false);
  const refreshProfilePromise = useRef<Promise<void> | null>(null);
  const accessRefreshPromise = useRef<Promise<string | null> | null>(null);
  const lastActivityAt = useRef(Date.now());
  const lastSessionExtensionAt = useRef(0);
  const expiringSession = useRef(false);

  const consumeSession = useCallback(() => {
    setUser(null); setAccessToken(null); setRefreshInSeconds(null); setIdleDeadline(null);
    setRemainingSeconds(0); setShowExpiryWarning(false); expiringSession.current = false;
  }, []);

  const commit = useCallback((session: AuthSession) => {
    const normalized = normalizeUser(session.user); const now = Date.now();
    setUser(normalized); setAccessToken(session.accessToken); setRefreshInSeconds(session.expiresInSeconds ?? null);
    setIdleDeadline(now + IDLE_TIMEOUT_MS); setRemainingSeconds(Math.ceil(IDLE_TIMEOUT_MS / 1000)); setShowExpiryWarning(false);
    lastActivityAt.current = now; lastSessionExtensionAt.current = now; expiringSession.current = false;
    return normalized;
  }, []);

  const refreshAccessToken = useCallback((): Promise<string | null> => {
    if (accessRefreshPromise.current) return accessRefreshPromise.current;
    const task = (async (): Promise<string | null> => {
      try { const next = await api.auth.refresh(); commit(next); return next.accessToken; }
      catch { consumeSession(); return null; }
    })();
    accessRefreshPromise.current = task;
    void task.finally(() => { if (accessRefreshPromise.current === task) accessRefreshPromise.current = null; });
    return task;
  }, [commit, consumeSession]);

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true);
    try { return commit(await api.auth.login(email, password)); }
    finally { setIsLoading(false); }
  }, [commit]);

  const refreshProfile = useCallback(async () => {
    if (!accessToken) return;
    if (!refreshProfilePromise.current) {
      refreshProfilePromise.current = (async () => {
        try { setUser(normalizeUser(await api.users.me(accessToken))); }
        catch (error) { if (error instanceof ApiError && error.status === 401) consumeSession(); throw error; }
        finally { refreshProfilePromise.current = null; }
      })();
    }
    return refreshProfilePromise.current;
  }, [accessToken, consumeSession]);

  const logout = useCallback(async () => {
    try { await api.auth.logout(accessToken); }
    finally { consumeSession(); setIsLoading(false); }
  }, [accessToken, consumeSession]);

  const recordActivity = useCallback(() => {
    if (!accessToken || isLoading) return;
    const now = Date.now();
    if (now - lastActivityAt.current < 1_000) return;
    lastActivityAt.current = now; setIdleDeadline(now + IDLE_TIMEOUT_MS); setRemainingSeconds(Math.ceil(IDLE_TIMEOUT_MS / 1000)); setShowExpiryWarning(false);
    if (now - lastSessionExtensionAt.current >= ACTIVE_REFRESH_INTERVAL_MS) {
      lastSessionExtensionAt.current = now;
      void refreshAccessToken();
    }
  }, [accessToken, isLoading, refreshAccessToken]);

  const continueSession = useCallback(async () => {
    setContinuingSession(true);
    try { if (await refreshAccessToken()) recordActivity(); }
    finally { setContinuingSession(false); }
  }, [recordActivity, refreshAccessToken]);

  useEffect(() => { configureSessionRecovery(refreshAccessToken); return () => configureSessionRecovery(null); }, [refreshAccessToken]);

  useEffect(() => {
    let mounted = true;
    void refreshAccessToken().finally(() => { if (mounted) setIsLoading(false); });
    return () => { mounted = false; };
  }, [refreshAccessToken]);

  useEffect(() => { if (accessToken) void refreshProfile().catch(() => undefined); }, [accessToken, refreshProfile]);

  useEffect(() => {
    if (!accessToken || !refreshInSeconds) return;
    const delay = Math.max(30_000, refreshInSeconds * 1000 - ACCESS_REFRESH_SKEW_MS);
    const timer = window.setTimeout(() => { if (Date.now() - lastActivityAt.current <= RECENT_ACTIVITY_WINDOW_MS) void refreshAccessToken(); }, delay);
    return () => window.clearTimeout(timer);
  }, [accessToken, refreshAccessToken, refreshInSeconds]);

  useEffect(() => {
    if (!accessToken) return;
    const events: Array<keyof WindowEventMap> = ["pointerdown", "keydown", "touchstart", "scroll"];
    events.forEach((event) => window.addEventListener(event, recordActivity, { passive: true }));
    const visibility = () => { if (document.visibilityState === "visible") recordActivity(); };
    document.addEventListener("visibilitychange", visibility);
    return () => { events.forEach((event) => window.removeEventListener(event, recordActivity)); document.removeEventListener("visibilitychange", visibility); };
  }, [accessToken, recordActivity]);

  useEffect(() => {
    if (!accessToken || !idleDeadline) return;
    const update = () => {
      const seconds = Math.max(0, Math.ceil((idleDeadline - Date.now()) / 1000));
      setRemainingSeconds(seconds);
      if (seconds === 0) {
        if (!expiringSession.current) { expiringSession.current = true; void logout(); }
        return;
      }
      setShowExpiryWarning(seconds * 1000 <= WARNING_WINDOW_MS);
    };
    update();
    const timer = window.setInterval(update, 1_000);
    return () => window.clearInterval(timer);
  }, [accessToken, idleDeadline, logout]);

  const value = useMemo<AuthContextValue>(() => ({
    user, accessToken, isAuthenticated: Boolean(user && accessToken), isLoading, login, refreshProfile, logout, consumeSession,
    hasAnyRole: (...roles) => Boolean(user?.roles?.some((role) => roles.includes(role))),
    hasPermission: (permission) => Boolean(user?.permissions?.includes(permission)),
  }), [accessToken, consumeSession, isLoading, login, logout, refreshProfile, user]);

  return <AuthContext.Provider value={value}>{children}{showExpiryWarning && accessToken ? <div className="session-expiry-backdrop" role="presentation"><section className="session-expiry-dialog" role="dialog" aria-modal="true" aria-labelledby="session-expiry-title"><span className="eyebrow">Session ending soon</span><h2 id="session-expiry-title">Keep your Pepekart session active?</h2><p>Your session will end in <strong>{displayRemaining(remainingSeconds)}</strong> because there has been no activity.</p><div className="action-row"><button className="button button-primary" type="button" onClick={() => void continueSession()} disabled={continuingSession}>{continuingSession ? "Continuing…" : "Continue session"}</button><button className="button button-secondary" type="button" onClick={() => void logout()} disabled={continuingSession}>Sign out</button></div></section></div> : null}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
