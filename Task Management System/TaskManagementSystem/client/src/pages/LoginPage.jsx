import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, getApiErrorMessage } from "../lib/apiClient";
import { setToken } from "../lib/auth";
import { fetchMe } from "../lib/me";
import { ThemeToggle } from "../components/ThemeToggle";

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const emailRef = useRef(null);
  const passwordRef = useRef(null);

  // Some browsers/password managers may autofill without firing onChange.
  // Force-clear on mount so the login page always starts empty.
  useEffect(() => {
    setEmail("");
    setPassword("");
    if (emailRef.current) emailRef.current.value = "";
    if (passwordRef.current) passwordRef.current.value = "";
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await api.post("/api/auth/login", { email, password });
      setToken(res.data.token);

      try {
        const me = await fetchMe();
        navigate(me?.role === "ADMIN" ? "/admin" : "/board");
      } catch {
        // If /me fails for any reason, fall back to the user board.
        navigate("/board");
      }
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
        <h1>Login</h1>
        {error ? <div className="error">{error}</div> : null}
        <form onSubmit={onSubmit} className="form" autoComplete="on">
          <label>
            Email
            <input
              ref={emailRef}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="email"
              name="username"
              autoComplete="username"
              autoCorrect="off"
              autoCapitalize="none"
              required
            />
          </label>
          <label>
            Password
            <input
              ref={passwordRef}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              name="current-password"
              autoComplete="current-password"
              required
            />
          </label>
          <button disabled={loading} type="submit">
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <p className="muted">
          No account? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  );
}
