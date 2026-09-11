import { Navigate, Outlet, useLocation } from "react-router-dom";
import type { Role } from "../domain";
import { useAuth } from "../auth/AuthProvider";

export function RequireAuth() {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <main className="route-message page-container"><span className="eyebrow">Securing your session</span><h1>Restoring your session…</h1></main>;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  return <Outlet />;
}

export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasAnyRole } = useAuth();
  if (!hasAnyRole(...roles)) return <Navigate to="/access-denied" replace />;
  return <Outlet />;
}
