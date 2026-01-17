// Generates a reasonably-unique id for client-side UI state (e.g., filter presets).
export function newUiId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return String(Date.now());
}
