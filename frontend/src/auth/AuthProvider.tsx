import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from "react";
import { api, configureSessionRecovery } from "../lib/api";
import { ApiError, type AuthSession, type Role, type User } from "../domain";

const SESSION_KEY = "marketly.stage.access-session";

type StoredSession = Pick<AuthSession, "accessToken" | "user">;

function normalizeUser(value: User): User {
  const roles: Role[] = value.roles?.length ? value.roles : value.role ? [value.role] : ["CUSTOMER"];
  return { ...value, role: value.role ?? roles[0], roles };
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSession;
    if (!parsed.accessToken || !parsed.user) return null;
    return { accessToken: parsed.accessToken, user: normalizeUser(parsed.user) };
  } catch {
    return null;
  }
}

function saveStoredSession(session: StoredSession | null) {
  if (!session) sessionStorage.removeItem(SESSION_KEY);
  else sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
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
  const initial = readStoredSession();
  const [user, setUser] = useState<User | null>(initial?.user ?? null);
  const [accessToken, setAccessToken] = useState<string | null>(initial?.accessToken ?? null);
  const [isLoading, setIsLoading] = useState(false);
  const [refreshInSeconds, setRefreshInSeconds] = useState<number | null>(null);
  const refreshToken = useRef<string | undefined>(undefined);
  const refreshPromise = useRef<Promise<void> | null>(null);
  const accessRefreshPromise = useRef<Promise<string | null> | null>(null);

  const consumeSession = useCallback(() => {
    setUser(null);
    setAccessToken(null);
    refreshToken.current = undefined;
    setRefreshInSeconds(null);
    saveStoredSession(null);
  }, []);

  const commit = useCallback((session: AuthSession) => {
    const normalized = normalizeUser(session.user);
    setUser(normalized);
    setAccessToken(session.accessToken);
    refreshToken.current = session.refreshToken;
    setRefreshInSeconds(session.expiresInSeconds ?? null);
    saveStoredSession({ accessToken: session.accessToken, user: normalized });
    return normalized;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const session = await api.auth.login(email, password);
      return commit(session);
    } finally {
      setIsLoading(false);
    }
  }, [commit]);

  const refreshProfile = useCallback(async () => {
    if (!accessToken) return;
    if (!refreshPromise.current) {
      refreshPromise.current = (async () => {
        try {
          const profile = await api.users.me(accessToken);
          const normalized = normalizeUser(profile);
          setUser(normalized);
          saveStoredSession({ accessToken, user: normalized });
        } catch (error) {
          if (error instanceof ApiError && error.status === 401) consumeSession();
          throw error;
        } finally {
          refreshPromise.current = null;
        }
      })();
    }
    return refreshPromise.current;
  }, [accessToken, consumeSession]);

  const refreshAccessToken = useCallback((): Promise<string | null> => {
    if (accessRefreshPromise.current) return accessRefreshPromise.current;
    const task = (async (): Promise<string | null> => {
      const token = refreshToken.current;
      if (!token) {
        consumeSession();
        return null;
      }
      try {
        const next = await api.auth.refresh(token);
        commit(next);
        return next.accessToken;
      } catch {
        consumeSession();
        return null;
      }
    })();
    accessRefreshPromise.current = task;
    void task.finally(() => {
      if (accessRefreshPromise.current === task) accessRefreshPromise.current = null;
    });
    return task;
  }, [commit, consumeSession]);

  const logout = useCallback(async () => {
    try {
      if (accessToken) await api.auth.logout(accessToken, refreshToken.current);
    } finally {
      consumeSession();
    }
  }, [accessToken, consumeSession]);

  useEffect(() => {
    configureSessionRecovery(refreshAccessToken);
    return () => configureSessionRecovery(null);
  }, [refreshAccessToken]);

  useEffect(() => {
    if (!accessToken) return;
    void refreshProfile().catch(() => undefined);
  }, [accessToken, refreshProfile]);

  useEffect(() => {
    if (!accessToken || !refreshToken.current || !refreshInSeconds) return;
    const delay = Math.max(30_000, (refreshInSeconds - 60) * 1000);
    const timer = window.setTimeout(() => { void refreshAccessToken(); }, delay);
    return () => window.clearTimeout(timer);
  }, [accessToken, refreshAccessToken, refreshInSeconds]);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    accessToken,
    isAuthenticated: Boolean(user && accessToken),
    isLoading,
    login,
    refreshProfile,
    logout,
    consumeSession,
    hasAnyRole: (...roles) => Boolean(user?.roles?.some((role) => roles.includes(role))),
    hasPermission: (permission) => Boolean(user?.permissions?.includes(permission)),
  }), [accessToken, consumeSession, isLoading, login, logout, refreshProfile, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
