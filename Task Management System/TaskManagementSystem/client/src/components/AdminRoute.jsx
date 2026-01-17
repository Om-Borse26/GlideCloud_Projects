import { Navigate } from "react-router-dom";
import { isAuthenticated } from "../lib/auth";
import { useMe } from "../lib/me";

export default function AdminRoute({ children }) {
  const { me, loading } = useMe();

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  if (loading) {
    return <div className="muted">Loading…</div>;
  }

  if (!me || me.role !== "ADMIN") {
    return <Navigate to="/board" replace />;
  }

  return children;
}
