import { useEffect, useMemo, useState } from "react";

export const defaultTaskFilters = {
  q: "",
  status: "ALL",
  priority: "ALL",
  label: "",
  focusOnly: false,
  due: "ALL", // ALL | OVERDUE | DUE_TODAY | DUE_7 | NO_DUE
};

function safeLower(s) {
  return String(s || "").toLowerCase();
}

function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

function parseDueDate(dueDate) {
  if (!dueDate) return null;
  const d = new Date(String(dueDate) + "T00:00:00");
  return Number.isNaN(d.getTime()) ? null : d;
}

export function taskMatchesQuery(task, query) {
  const q = safeLower(query).trim();
  if (!q) return true;

  const parts = [];
  parts.push(task?.title);
  parts.push(task?.description);

  const labels = Array.isArray(task?.labels) ? task.labels : [];
  for (const l of labels) parts.push(l);

  const comments = Array.isArray(task?.comments) ? task.comments : [];
  for (const c of comments) parts.push(c?.message);

  const decisions = Array.isArray(task?.decisions) ? task.decisions : [];
  for (const d of decisions) parts.push(d?.message);

  const haystack = safeLower(parts.filter(Boolean).join("\n"));
  return haystack.includes(q);
}

export function applyTaskFilters(tasks, filters) {
  const list = Array.isArray(tasks) ? tasks : [];
  const f = { ...defaultTaskFilters, ...(filters || {}) };

  const today = startOfToday();
  const todayKey = today.toISOString().slice(0, 10);

  return list.filter((t) => {
    if (!taskMatchesQuery(t, f.q)) return false;

    if (
      f.status &&
      f.status !== "ALL" &&
      String(t.status) !== String(f.status)
    ) {
      return false;
    }

    if (
      f.priority &&
      f.priority !== "ALL" &&
      String(t.priority) !== String(f.priority)
    ) {
      return false;
    }

    if (f.label && String(f.label).trim()) {
      const labels = Array.isArray(t.labels) ? t.labels : [];
      if (!labels.includes(String(f.label).trim())) return false;
    }

    if (f.focusOnly) {
      if (!t.focus) return false;
    }

    if (f.due && f.due !== "ALL") {
      const due = parseDueDate(t.dueDate);
      if (f.due === "NO_DUE") return !due;
      if (!due) return false;

      if (f.due === "OVERDUE") {
        return due.getTime() < today.getTime() && String(t.status) !== "DONE";
      }

      if (f.due === "DUE_TODAY") {
        return String(t.dueDate) === todayKey;
      }

      if (f.due === "DUE_7") {
        const end = new Date(today);
        end.setDate(end.getDate() + 7);
        return (
          due.getTime() >= today.getTime() && due.getTime() <= end.getTime()
        );
      }
    }

    return true;
  });
}

export function collectLabels(tasks) {
  const set = new Set();
  for (const t of Array.isArray(tasks) ? tasks : []) {
    for (const l of Array.isArray(t?.labels) ? t.labels : []) {
      const s = String(l || "").trim();
      if (s) set.add(s);
    }
  }
  return Array.from(set).sort((a, b) => a.localeCompare(b));
}

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

export function useStoredTaskFilters(storageKey) {
  const [filters, setFilters] = useState(() => {
    const stored = loadJson(storageKey, null);
    return stored
      ? { ...defaultTaskFilters, ...stored }
      : { ...defaultTaskFilters };
  });

  useEffect(() => {
    saveJson(storageKey, filters);
  }, [storageKey, filters]);

  return [filters, setFilters];
}

export function useFilteredTasks(tasks, filters) {
  return useMemo(() => applyTaskFilters(tasks, filters), [tasks, filters]);
}
