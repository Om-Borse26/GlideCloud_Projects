import { useMemo, useState } from "react";
import { collectLabels, defaultTaskFilters } from "../lib/taskFilters";
import { newUiId } from "../lib/ids";

function loadJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function saveJson(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore
  }
}

const PRESETS_KEY = "filters:presets:v1";

export default function FilterBar({
  tasks,
  filters,
  onChange,
  title = "Filters",
}) {
  const labels = useMemo(() => collectLabels(tasks), [tasks]);

  const presets = loadJson(PRESETS_KEY, []);
  const [presetName, setPresetName] = useState("");

  function set(next) {
    onChange({ ...defaultTaskFilters, ...filters, ...next });
  }

  function savePreset() {
    const name = presetName.trim();
    if (!name) return;
    const next = Array.isArray(presets) ? presets.slice() : [];
    const id = newUiId();
    next.unshift({ id, name, filters: { ...filters } });
    saveJson(PRESETS_KEY, next.slice(0, 20));
    setPresetName("");
  }

  function applyPreset(id) {
    const p = (Array.isArray(presets) ? presets : []).find((x) => x.id === id);
    if (!p) return;
    onChange({ ...defaultTaskFilters, ...(p.filters || {}) });
  }

  function deletePreset(id) {
    const next = (Array.isArray(presets) ? presets : []).filter(
      (x) => x.id !== id
    );
    saveJson(PRESETS_KEY, next);
  }

  return (
    <section className="filterBar">
      <div className="filterBarHeader">
        <strong>{title}</strong>
        <div className="filterBarPresets">
          <select
            className="select"
            defaultValue=""
            onChange={(e) => {
              const id = e.target.value;
              if (!id) return;
              applyPreset(id);
              e.target.value = "";
            }}
            title="Load a saved filter preset"
          >
            <option value="">Load preset…</option>
            {(Array.isArray(presets) ? presets : []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>

          {(Array.isArray(presets) ? presets : []).length ? (
            <select
              className="select"
              defaultValue=""
              onChange={(e) => {
                const id = e.target.value;
                if (!id) return;
                deletePreset(id);
                e.target.value = "";
              }}
              title="Delete a saved preset"
            >
              <option value="">Delete preset…</option>
              {(Array.isArray(presets) ? presets : []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          ) : null}
        </div>
      </div>

      <div className="filterBarGrid">
        <input
          className="input"
          placeholder="Search (titles, labels, comments, decisions)…"
          value={filters.q}
          onChange={(e) => set({ q: e.target.value })}
        />

        <select
          className="select"
          value={filters.status}
          onChange={(e) => set({ status: e.target.value })}
          title="Status"
        >
          <option value="ALL">All statuses</option>
          <option value="TODO">TODO</option>
          <option value="IN_PROGRESS">IN PROGRESS</option>
          <option value="DONE">DONE</option>
        </select>

        <select
          className="select"
          value={filters.priority}
          onChange={(e) => set({ priority: e.target.value })}
          title="Priority"
        >
          <option value="ALL">All priorities</option>
          <option value="LOW">LOW</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HIGH">HIGH</option>
        </select>

        <select
          className="select"
          value={filters.label}
          onChange={(e) => set({ label: e.target.value })}
          title="Label"
        >
          <option value="">All labels</option>
          {labels.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>

        <select
          className="select"
          value={filters.due}
          onChange={(e) => set({ due: e.target.value })}
          title="Due date"
        >
          <option value="ALL">Any due date</option>
          <option value="OVERDUE">Overdue</option>
          <option value="DUE_TODAY">Due today</option>
          <option value="DUE_7">Due next 7 days</option>
          <option value="NO_DUE">No due date</option>
        </select>

        <label
          className="muted"
          style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}
        >
          <input
            type="checkbox"
            checked={Boolean(filters.focusOnly)}
            onChange={(e) => set({ focusOnly: e.target.checked })}
          />
          Focus only
        </label>
      </div>

      <div className="filterBarFooter">
        <div className="inlineRow" style={{ margin: 0 }}>
          <input
            className="input"
            placeholder="Preset name…"
            value={presetName}
            onChange={(e) => setPresetName(e.target.value)}
            maxLength={40}
          />
          <button
            type="button"
            className="secondary"
            onClick={savePreset}
            disabled={!presetName.trim()}
          >
            Save preset
          </button>
          <button
            type="button"
            className="secondary"
            onClick={() => onChange({ ...defaultTaskFilters })}
          >
            Reset
          </button>
        </div>
      </div>
    </section>
  );
}
