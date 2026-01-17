import { useEffect, useState } from "react";
import { api, getApiErrorMessage } from "./apiClient";
import { clearToken, isAuthenticated } from "./auth";

export async function fetchMe() {
  const res = await api.get("/api/auth/me");
  return res.data;
}

export function useMe() {
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(isAuthenticated());
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function run() {
      if (!isAuthenticated()) {
        setLoading(false);
        setMe(null);
        return;
      }

      setLoading(true);
      setError("");
      try {
        const data = await fetchMe();
        if (!cancelled) setMe(data);
      } catch (err) {
        if (err?.response?.status === 401) {
          clearToken();
        }
        if (!cancelled) setError(getApiErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    run();
    return () => {
      cancelled = true;
    };
  }, []);

  return { me, loading, error };
}
