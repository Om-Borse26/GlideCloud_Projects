import axios from "axios";

const TASKS_CACHE_KEY = "offline:tasksCache:v1";
const QUEUE_KEY = "offline:queue:v1";

const listeners = new Set();
let lastSyncError = "";

function emit() {
  const state = getOfflineState();
  for (const cb of listeners) {
    try {
      cb(state);
    } catch {
      // ignore
    }
  }
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

export function getCachedTasks() {
  const tasks = loadJson(TASKS_CACHE_KEY, []);
  return Array.isArray(tasks) ? tasks : [];
}

export function setCachedTasks(tasks) {
  saveJson(TASKS_CACHE_KEY, Array.isArray(tasks) ? tasks : []);
  emit();
}

export function getQueue() {
  const q = loadJson(QUEUE_KEY, []);
  return Array.isArray(q) ? q : [];
}

function setQueue(q) {
  saveJson(QUEUE_KEY, Array.isArray(q) ? q : []);
  emit();
}

export function getOfflineState() {
  const online = typeof navigator === "undefined" ? true : navigator.onLine;
  const queueCount = getQueue().length;
  return { online, offline: !online, queueCount, lastSyncError };
}

export function subscribeOfflineState(cb) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function nowIso() {
  return new Date().toISOString();
}

function normalizeUrl(url) {
  return String(url || "");
}

function methodOf(config) {
  return String(config?.method || "get").toLowerCase();
}

function isTasksList(url) {
  return url === "/api/tasks";
}

function isTaskById(url) {
  return /^\/api\/tasks\/[^/]+$/.test(url);
}

function getTaskIdFromUrl(url) {
  const m = String(url || "").match(/^\/api\/tasks\/([^/]+)/);
  return m ? m[1] : null;
}

function optimisticUpdate(cacheTasks, config) {
  const method = methodOf(config);
  const url = normalizeUrl(config?.url);
  const data = config?.data;

  let tasks = Array.isArray(cacheTasks) ? cacheTasks.slice() : [];

  // Create
  if (method === "post" && isTasksList(url)) {
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const created = {
      id: `local-${uuid()}`,
      title: body?.title || "New task",
      description: body?.description || "",
      priority: body?.priority || "MEDIUM",
      status: "TODO",
      archived: false,
      archivedAt: null,
      dueDate: body?.dueDate || null,
      labels: [],
      blockedByTaskIds: [],
      comments: [],
      checklist: [],
      decisions: [],
      focus: false,
      timeBudgetMinutes: null,
      totalLoggedMinutes: 0,
      activeTimerStartedAt: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    tasks.push(created);
    return { tasks, responseData: created };
  }

  // Delete
  if (method === "delete" && isTaskById(url)) {
    const id = getTaskIdFromUrl(url);
    tasks = tasks.filter((t) => t.id !== id);
    return { tasks, responseData: null };
  }

  // Update core task
  if (method === "put" && isTaskById(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      return {
        ...t,
        title: body?.title ?? t.title,
        description: body?.description ?? t.description,
        priority: body?.priority ?? t.priority,
        dueDate: body?.dueDate ?? t.dueDate,
        updatedAt: nowIso(),
      };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Move (best-effort: update status)
  if (method === "post" && url === "/api/tasks/move") {
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const taskId = body?.taskId;
    const toStatus = body?.toStatus;
    if (taskId && toStatus) {
      tasks = tasks.map((t) =>
        t.id === taskId ? { ...t, status: toStatus, updatedAt: nowIso() } : t
      );
    }
    return { tasks, responseData: tasks };
  }

  // Labels
  if (method === "put" && /\/api\/tasks\/[^/]+\/labels$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const labels = Array.isArray(body?.labels) ? body.labels : [];
    tasks = tasks.map((t) =>
      t.id === id ? { ...t, labels, updatedAt: nowIso() } : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Focus
  if (method === "put" && /\/api\/tasks\/[^/]+\/focus$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    tasks = tasks.map((t) =>
      t.id === id
        ? { ...t, focus: Boolean(body?.focus), updatedAt: nowIso() }
        : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Time budget
  if (method === "put" && /\/api\/tasks\/[^/]+\/time-budget$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const value = body?.timeBudgetMinutes;
    tasks = tasks.map((t) =>
      t.id === id
        ? { ...t, timeBudgetMinutes: value ?? null, updatedAt: nowIso() }
        : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Recurrence
  if (method === "put" && /\/api\/tasks\/[^/]+\/recurrence$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const recurrence = body?.frequency ? { ...body } : null;
    tasks = tasks.map((t) =>
      t.id === id ? { ...t, recurrence, updatedAt: nowIso() } : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Dependencies
  if (method === "put" && /\/api\/tasks\/[^/]+\/dependencies$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const blockedByTaskIds = Array.isArray(body?.blockedByTaskIds)
      ? body.blockedByTaskIds
      : [];
    tasks = tasks.map((t) =>
      t.id === id ? { ...t, blockedByTaskIds, updatedAt: nowIso() } : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Archive
  if (method === "put" && /\/api\/tasks\/[^/]+\/archive$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const nextArchived = Boolean(body?.archived);
    tasks = tasks.map((t) =>
      t.id === id
        ? {
            ...t,
            archived: nextArchived,
            archivedAt: nextArchived ? nowIso() : null,
            updatedAt: nowIso(),
          }
        : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Comments
  if (method === "post" && /\/api\/tasks\/[^/]+\/comments$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const msg = String(body?.message || "").trim();
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      const comments = Array.isArray(t.comments) ? t.comments.slice() : [];
      comments.push({
        id: `local-${uuid()}`,
        message: msg,
        createdAt: nowIso(),
        authorEmail: "(offline)",
      });
      return { ...t, comments, updatedAt: nowIso() };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Decisions
  if (method === "post" && /\/api\/tasks\/[^/]+\/decisions$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const msg = String(body?.message || "").trim();
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      const decisions = Array.isArray(t.decisions) ? t.decisions.slice() : [];
      decisions.push({
        id: `local-${uuid()}`,
        message: msg,
        createdAt: nowIso(),
        authorEmail: "(offline)",
      });
      return { ...t, decisions, updatedAt: nowIso() };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Checklist add
  if (method === "post" && /\/api\/tasks\/[^/]+\/checklist$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const text = String(body?.text || "").trim();
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      const checklist = Array.isArray(t.checklist) ? t.checklist.slice() : [];
      checklist.push({
        id: `local-${uuid()}`,
        text,
        done: false,
        position: checklist.length,
      });
      return { ...t, checklist, updatedAt: nowIso() };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Checklist update
  if (method === "put" && /\/api\/tasks\/[^/]+\/checklist\/[^/]+$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    const body = typeof data === "string" ? JSON.parse(data) : data;
    const itemId = String(url.split("/checklist/")[1] || "");
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      const checklist = Array.isArray(t.checklist) ? t.checklist.slice() : [];
      const next = checklist.map((i) =>
        i.id === itemId ? { ...i, ...body } : i
      );
      return { ...t, checklist: next, updatedAt: nowIso() };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  // Timer start/stop (minimal)
  if (method === "post" && /\/api\/tasks\/[^/]+\/timer\/start$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    tasks = tasks.map((t) =>
      t.id === id
        ? { ...t, activeTimerStartedAt: nowIso(), updatedAt: nowIso() }
        : t
    );
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  if (method === "post" && /\/api\/tasks\/[^/]+\/timer\/stop$/.test(url)) {
    const id = getTaskIdFromUrl(url);
    tasks = tasks.map((t) => {
      if (t.id !== id) return t;
      const startedAt = t.activeTimerStartedAt
        ? new Date(t.activeTimerStartedAt).getTime()
        : null;
      const mins = startedAt
        ? Math.max(0, Math.round((Date.now() - startedAt) / 60000))
        : 0;
      const nextLogged = Number(t.totalLoggedMinutes || 0) + mins;
      return {
        ...t,
        activeTimerStartedAt: null,
        totalLoggedMinutes: nextLogged,
        updatedAt: nowIso(),
      };
    });
    const updated = tasks.find((t) => t.id === id) || null;
    return { tasks, responseData: updated };
  }

  return { tasks, responseData: null };
}

export function enqueueOfflineRequest(config) {
  const method = methodOf(config);
  const url = normalizeUrl(config?.url);

  // Only queue mutations
  if (["get", "head", "options"].includes(method)) return;

  const q = getQueue();
  q.push({
    id: uuid(),
    createdAt: nowIso(),
    method,
    url,
    data: config?.data ?? null,
    params: config?.params ?? null,
  });
  setQueue(q);

  // Update cache optimistically
  const cached = getCachedTasks();
  const { tasks } = optimisticUpdate(cached, config);
  setCachedTasks(tasks);
}

export async function flushOfflineQueue(api) {
  if (typeof navigator !== "undefined" && !navigator.onLine) return;
  const q = getQueue();
  if (!q.length) return;

  lastSyncError = "";

  const remaining = [];
  for (const item of q) {
    try {
      await api.request({
        method: item.method,
        url: item.url,
        data: item.data,
        params: item.params,
        __skipOffline: true,
      });
    } catch {
      // Keep the remaining items; best-effort.
      remaining.push(item);
      lastSyncError = "Some changes failed to sync. We will retry.";
    }
  }

  setQueue(remaining);

  // Always reload cache from server if possible.
  try {
    const res = await api.get("/api/tasks", { __skipOffline: true });
    setCachedTasks(res.data);
  } catch {
    // ignore
  }

  emit();
}

export function installOfflineSupport(api) {
  // Keep cache fresh when online.
  api.interceptors.response.use(
    (res) => {
      try {
        const url = normalizeUrl(res?.config?.url);
        if (
          methodOf(res?.config) === "get" &&
          isTasksList(url) &&
          Array.isArray(res.data)
        ) {
          setCachedTasks(res.data);
        }
      } catch {
        // ignore
      }
      return res;
    },
    (err) => Promise.reject(err)
  );

  api.interceptors.request.use((config) => {
    if (config?.__skipOffline) return config;
    if (typeof navigator === "undefined") return config;

    if (navigator.onLine === false) {
      config.adapter = async (cfg) => {
        const url = normalizeUrl(cfg?.url);
        const method = methodOf(cfg);
        const cached = getCachedTasks();

        // GET tasks list
        if (method === "get" && isTasksList(url)) {
          return {
            data: cached,
            status: 200,
            statusText: "OK (offline cache)",
            headers: {},
            config: cfg,
            request: {},
          };
        }

        // GET single task
        if (method === "get" && isTaskById(url)) {
          const id = getTaskIdFromUrl(url);
          const found = cached.find((t) => t.id === id) || null;
          return {
            data: found,
            status: found ? 200 : 404,
            statusText: found
              ? "OK (offline cache)"
              : "Not Found (offline cache)",
            headers: {},
            config: cfg,
            request: {},
          };
        }

        // GET search
        if (method === "get" && url === "/api/tasks/search") {
          const q = String(cfg?.params?.q || "")
            .trim()
            .toLowerCase();
          const filtered = !q
            ? cached
            : cached.filter((t) => {
                const text = [
                  t?.title,
                  t?.description,
                  ...(Array.isArray(t?.labels) ? t.labels : []),
                  ...(Array.isArray(t?.comments)
                    ? t.comments.map((c) => c?.message)
                    : []),
                  ...(Array.isArray(t?.decisions)
                    ? t.decisions.map((d) => d?.message)
                    : []),
                ]
                  .filter(Boolean)
                  .join("\n")
                  .toLowerCase();
                return text.includes(q);
              });
          return {
            data: filtered,
            status: 200,
            statusText: "OK (offline cache)",
            headers: {},
            config: cfg,
            request: {},
          };
        }

        // Queue mutations + optimistic cache update
        if (!["get", "head", "options"].includes(method)) {
          enqueueOfflineRequest(cfg);
          const nextTasks = getCachedTasks();
          const { responseData } = optimisticUpdate(nextTasks, cfg);
          return {
            data: responseData,
            status: 202,
            statusText: "Accepted (queued offline)",
            headers: { "x-offline-queued": "1" },
            config: cfg,
            request: {},
          };
        }

        // Default: network error offline
        return Promise.reject(
          new axios.AxiosError("Offline", "ERR_NETWORK", cfg)
        );
      };
    }

    return config;
  });

  const onOnline = () => {
    emit();
    flushOfflineQueue(api);
  };
  const onOffline = () => emit();

  window.addEventListener("online", onOnline);
  window.addEventListener("offline", onOffline);

  emit();

  return () => {
    window.removeEventListener("online", onOnline);
    window.removeEventListener("offline", onOffline);
  };
}

// Optional helper: allows flushing via UI.
export async function syncNow(api) {
  await flushOfflineQueue(api);
}
