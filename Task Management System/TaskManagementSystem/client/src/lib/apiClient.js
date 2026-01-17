import axios from "axios";
import { getToken, clearToken } from "./auth";
import { installOfflineSupport } from "./offlineQueue";

// In dev, Vite proxies /api to the backend (see vite.config.js).
// In prod, set VITE_API_BASE_URL if frontend and backend are on different origins.
const baseURL = import.meta.env.VITE_API_BASE_URL || "";

export const api = axios.create({
  baseURL,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err?.response?.status === 401) {
      // Basic capstone UX: clear token on unauthorized.
      clearToken();
    }
    return Promise.reject(err);
  }
);

// Offline-first support (cache tasks + queue mutations)
installOfflineSupport(api);

export function getApiErrorMessage(error) {
  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    "Request failed"
  );
}
