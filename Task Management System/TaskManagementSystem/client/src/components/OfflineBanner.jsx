import { useEffect, useState } from "react";
import { api } from "../lib/apiClient";
import {
  getOfflineState,
  subscribeOfflineState,
  syncNow,
} from "../lib/offlineQueue";

export default function OfflineBanner() {
  const [state, setState] = useState(() => getOfflineState());
  const [busy, setBusy] = useState(false);

  useEffect(() => subscribeOfflineState(setState), []);

  if (state.online && state.queueCount === 0) return null;

  return (
    <div className="offlineBanner" role="status" aria-live="polite">
      <div>
        <strong>{state.offline ? "Offline" : "Sync pending"}</strong>
        <span className="muted" style={{ marginLeft: "0.5rem" }}>
          {state.offline
            ? "Changes will be queued and synced when you reconnect."
            : `${state.queueCount} change(s) waiting to sync.`}
        </span>
        {state.lastSyncError ? (
          <div className="muted small" style={{ marginTop: "0.25rem" }}>
            {state.lastSyncError}
          </div>
        ) : null}
      </div>
      <div className="offlineBannerActions">
        <span className="pill">Queued: {state.queueCount}</span>
        <button
          type="button"
          className="secondary"
          disabled={busy || state.offline || state.queueCount === 0}
          onClick={async () => {
            setBusy(true);
            try {
              await syncNow(api);
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? "Syncing…" : "Sync now"}
        </button>
      </div>
    </div>
  );
}
