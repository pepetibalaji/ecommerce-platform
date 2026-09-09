import { Navigate, Outlet, useLocation } from "react-router-dom";
import type { Role } from "../domain";
import { useAuth } from "../auth/AuthProvider";

export function RequireAuth() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  return <Outlet />;
}

export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasAnyRole } = useAuth();
  if (!hasAnyRole(...roles)) return <Navigate to="/access-denied" replace />;
  return <Outlet />;
}
