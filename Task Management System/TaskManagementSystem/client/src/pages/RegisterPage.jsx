import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, getApiErrorMessage } from "../lib/apiClient";
import { ThemeToggle } from "../components/ThemeToggle";

export default function RegisterPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await api.post("/api/auth/register", { email, password });
      navigate("/login");
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <ThemeToggle />
      <div className="card">
        <div className="authBrand">
          <div className="authBrandName">NebulaFlow</div>
          <div className="authBrandTagline">
            A dreamy way to plan, focus, and finish.
          </div>
        </div>
        <h1>Register</h1>
        {error ? <div className="error">{error}</div> : null}
        <form onSubmit={onSubmit} className="form">
          <label>
            Email
            <input
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="email"
              required
            />
          </label>
          <label>
            Password
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              required
            />
          </label>
          <button disabled={loading} type="submit">
            {loading ? "Creating…" : "Create account"}
          </button>
        </form>
        <p className="muted">
          Already registered? <Link to="/login">Login</Link>
        </p>
      </div>
    </div>
  );
}
