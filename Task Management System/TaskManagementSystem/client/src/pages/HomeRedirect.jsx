import { Navigate } from "react-router-dom";
import { isAuthenticated } from "../lib/auth";
import { useMe } from "../lib/me";

export default function HomeRedirect() {
  const authed = isAuthenticated();
  const { me, loading } = useMe();

  if (!authed) {
    return <Navigate to="/login" replace />;
  }

  if (loading) {
    return (
      <div className="page">
        <div className="card">
          <div className="muted">Loading…</div>
        </div>
      </div>
    );
  }

  if (me?.role === "ADMIN") {
    return <Navigate to="/admin" replace />;
  }

  return <Navigate to="/board" replace />;
}
