import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, getApiErrorMessage } from "../lib/apiClient";
import { clearToken } from "../lib/auth";
import { useMe } from "../lib/me";
import KanbanBoard from "../ui/KanbanBoard";
import CommandPalette from "../ui/CommandPalette";
import TaskDetailsModal from "../ui/TaskDetailsModal";
import { ThemeToggle } from "../components/ThemeToggle";
import OfflineBanner from "../components/OfflineBanner";
import FilterBar from "../components/FilterBar";
import LoadingSpinner from "../components/LoadingSpinner";
import DropdownMenu from "../components/DropdownMenu";
import { useFilteredTasks, useStoredTaskFilters } from "../lib/taskFilters";
import { fetchTasks, useTaskMutations } from "../lib/tasksClient";

export default function BoardPage() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { me } = useMe();

  const [filters, setFilters] = useStoredTaskFilters("board:filters:v1");

  const [focusMode, setFocusMode] = useState(
    () => localStorage.getItem("board:focusMode") === "1"
  );

  const [showArchived, setShowArchived] = useState(() => {
    const v = localStorage.getItem("board:showArchived");
    if (v != null) return v === "1";
    // Back-compat with earlier key
    return localStorage.getItem("board:showArchivedDone") === "1";
  });

  const [pomodoroTaskId, setPomodoroTaskId] = useState(null);
  const [pomodoroRunning, setPomodoroRunning] = useState(false);
  const [pomodoroEndsAt, setPomodoroEndsAt] = useState(null);
  const [pomodoroSecondsLeft, setPomodoroSecondsLeft] = useState(25 * 60);
  const [pomodoroStatus, setPomodoroStatus] = useState("");

  const [selectedTaskIds, setSelectedTaskIds] = useState(() => new Set());
  const [bulkPriority, setBulkPriority] = useState("MEDIUM");
  const [bulkLabel, setBulkLabel] = useState("");
  const [bulkStatus, setBulkStatus] = useState("TODO");

  const [detailsTaskId, setDetailsTaskId] = useState(null);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const [layoutMode, setLayoutMode] = useState(
    () => localStorage.getItem("board:layoutMode") === "1"
  );
  const [wipLimits, setWipLimits] = useState(() => {
    try {
      const raw = localStorage.getItem("board:wipLimits:v1");
      const parsed = raw ? JSON.parse(raw) : null;
      if (!parsed || typeof parsed !== "object") {
        return { TODO: null, IN_PROGRESS: null, DONE: null };
      }
      const pick = (k) => {
        const v = parsed?.[k];
        const n = typeof v === "number" ? v : null;
        return Number.isFinite(n) && n > 0 ? Math.floor(n) : null;
      };
      return {
        TODO: pick("TODO"),
        IN_PROGRESS: pick("IN_PROGRESS"),
        DONE: pick("DONE"),
      };
    } catch {
      return { TODO: null, IN_PROGRESS: null, DONE: null };
    }
  });
  const [columnOrder, setColumnOrder] = useState(() => {
    try {
      const raw = localStorage.getItem("board:columnOrder");
      const parsed = raw ? JSON.parse(raw) : null;
      const ok =
        Array.isArray(parsed) && parsed.every((x) => typeof x === "string");
      return ok ? parsed : ["TODO", "IN_PROGRESS", "DONE"];
    } catch {
      return ["TODO", "IN_PROGRESS", "DONE"];
    }
  });

  useEffect(() => {
    localStorage.setItem("board:layoutMode", layoutMode ? "1" : "0");
  }, [layoutMode]);

  useEffect(() => {
    localStorage.setItem("board:focusMode", focusMode ? "1" : "0");
  }, [focusMode]);

  useEffect(() => {
    localStorage.setItem("board:showArchived", showArchived ? "1" : "0");
  }, [showArchived]);

  useEffect(() => {
    localStorage.setItem("board:columnOrder", JSON.stringify(columnOrder));
  }, [columnOrder]);

  useEffect(() => {
    try {
      localStorage.setItem("board:wipLimits:v1", JSON.stringify(wipLimits));
    } catch {
      // ignore
    }
  }, [wipLimits]);

  const filteredTasks = useFilteredTasks(tasks, filters);

  const displayedTasks = useMemo(() => {
    let base = filteredTasks;

    if (focusMode) {
      base = base.filter((t) => t.focus);
    }

    // Keep the board clean: hide archived DONE tasks by default.
    if (!showArchived) {
      base = base.filter((t) => !t.archived);
    }

    return base;
  }, [filteredTasks, focusMode, showArchived]);

  const focusTasks = useMemo(() => {
    return tasks
      .filter((t) => (showArchived ? true : !t.archived))
      .filter((t) => t.status !== "DONE")
      .filter((t) => t.focus);
  }, [tasks, showArchived]);

  useEffect(() => {
    if (pomodoroTaskId) {
      const exists = focusTasks.some((t) => t.id === pomodoroTaskId);
      if (exists) return;
    }
    setPomodoroTaskId(focusTasks[0]?.id || null);
  }, [focusTasks, pomodoroTaskId]);

  useEffect(() => {
    if (!pomodoroRunning) return;
    if (!pomodoroEndsAt) return;

    const tick = () => {
      const left = Math.max(0, Math.ceil((pomodoroEndsAt - Date.now()) / 1000));
      setPomodoroSecondsLeft(left);
    };

    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [pomodoroRunning, pomodoroEndsAt]);

  useEffect(() => {
    if (!pomodoroRunning) return;
    if (pomodoroSecondsLeft !== 0) return;
    // Auto-stop when a session ends.
    (async () => {
      if (!pomodoroTaskId) return;
      setPomodoroStatus("Pomodoro complete");
      setPomodoroRunning(false);
      try {
        const res = await api.post(`/api/tasks/${pomodoroTaskId}/timer/stop`, {
          note: "Pomodoro completed",
        });
        setTasks((prev) =>
          prev.map((t) => (t.id === pomodoroTaskId ? res.data : t))
        );
      } catch {
        // Ignore: we'll refresh on next reload.
      }
    })();
  }, [pomodoroRunning, pomodoroSecondsLeft, pomodoroTaskId]);

  useEffect(() => {
    // Restore pomodoro state.
    try {
      const raw = localStorage.getItem("board:pomodoro");
      const s = raw ? JSON.parse(raw) : null;
      if (!s) return;

      const endsAt = typeof s.endsAt === "number" ? s.endsAt : null;
      const taskId = typeof s.taskId === "string" ? s.taskId : null;
      const running = Boolean(s.running) && endsAt && endsAt > Date.now();

      setPomodoroTaskId(taskId);
      setPomodoroRunning(running);
      setPomodoroEndsAt(running ? endsAt : null);
      if (running) {
        setPomodoroSecondsLeft(
          Math.max(0, Math.ceil((endsAt - Date.now()) / 1000))
        );
      }
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(
        "board:pomodoro",
        JSON.stringify({
          taskId: pomodoroTaskId,
          running: pomodoroRunning,
          endsAt: pomodoroEndsAt,
        })
      );
    } catch {
      // ignore
    }
  }, [pomodoroTaskId, pomodoroRunning, pomodoroEndsAt]);

  function formatMmSs(totalSeconds) {
    const s = Math.max(0, Number(totalSeconds) | 0);
    const mm = Math.floor(s / 60);
    const ss = s % 60;
    return `${mm}:${String(ss).padStart(2, "0")}`;
  }

  const tasksByStatus = useMemo(() => {
    const by = { TODO: [], IN_PROGRESS: [], DONE: [] };
    for (const t of displayedTasks) {
      by[t.status]?.push(t);
    }
    for (const k of Object.keys(by)) {
      by[k].sort((a, b) => {
        const ap = a.pinned ? 1 : 0;
        const bp = b.pinned ? 1 : 0;
        if (bp !== ap) return bp - ap;
        return (a.position ?? 0) - (b.position ?? 0);
      });
    }
    return by;
  }, [displayedTasks]);

  const overdueTasks = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return tasks
      .filter((t) => t.status !== "DONE" && t.dueDate)
      .filter((t) => {
        const due = new Date(String(t.dueDate) + "T00:00:00");
        return due.getTime() < today.getTime();
      })
      .sort((a, b) => {
        const ad = new Date(String(a.dueDate) + "T00:00:00").getTime();
        const bd = new Date(String(b.dueDate) + "T00:00:00").getTime();
        return ad - bd;
      });
  }, [tasks]);

  async function reload() {
    setError("");
    setLoading(true);
    try {
      const data = await fetchTasks();
      setTasks(data);
      setSelectedTaskIds((prev) => {
        const existing = new Set(data.map((t) => t.id));
        const next = new Set();
        for (const id of prev) if (existing.has(id)) next.add(id);
        return next;
      });
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  function toggleSelected(taskId) {
    setSelectedTaskIds((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) next.delete(taskId);
      else next.add(taskId);
      return next;
    });
  }

  async function bulkAction(action, extra = {}) {
    const taskIds = Array.from(selectedTaskIds);
    if (!taskIds.length) return;
    setError("");
    try {
      const res = await api.post("/api/tasks/bulk", {
        taskIds,
        action,
        ...extra,
      });
      setTasks(res.data);
      setSelectedTaskIds(new Set());
      setBulkLabel("");
    } catch (err) {
      setError(getApiErrorMessage(err));
      await reload();
    }
  }

  const {
    addComment,
    updateLabels,
    updateArchived,
    addChecklistItem,
    updateChecklistItem,
    updateFocus: updateFocusRaw,
    updateTimeBudget,
    startTimer,
    stopTimer,
    addDecision,
    updateRecurrence,
    updateDependencies,
  } = useTaskMutations({ setTasks, setError });

  async function updateFocus(taskId, focus) {
    const max = 3;
    if (focus) {
      const current = tasks
        .filter((t) => (showArchived ? true : !t.archived))
        .filter((t) => t.status !== "DONE")
        .filter((t) => t.focus).length;
      if (current >= max) {
        setError("Focus mode is limited to 3 tasks. Unfocus one first.");
        return;
      }
    }
    await updateFocusRaw(taskId, focus);
  }

  async function createTask(title, priority, description, dueDate) {
    const res = await api.post("/api/tasks", {
      title,
      description: description || "",
      priority,
      dueDate: dueDate || null,
    });
    setTasks((prev) => [...prev, res.data]);
  }

  async function quickMove(taskId, fromStatus, toStatus) {
    setError("");
    try {
      const res = await api.post("/api/tasks/move", {
        taskId,
        fromStatus,
        toStatus,
        toIndex: 0,
      });
      setTasks(res.data);
    } catch (err) {
      setError(getApiErrorMessage(err));
      await reload();
    }
  }

  async function updateTaskPriority(task, priority) {
    const previous = tasks;
    setTasks((prev) =>
      prev.map((t) => (t.id === task.id ? { ...t, priority } : t))
    );
    try {
      const res = await api.put(`/api/tasks/${task.id}`, {
        title: task.title,
        description: task.description || "",
        priority,
        dueDate: task.dueDate || null,
      });
      setTasks((prev) => prev.map((t) => (t.id === task.id ? res.data : t)));
    } catch (err) {
      setTasks(previous);
      setError(getApiErrorMessage(err));
    }
  }

  async function deleteTask(taskId) {
    await api.delete(`/api/tasks/${taskId}`);
    setTasks((prev) => prev.filter((t) => t.id !== taskId));
    // Backend reindexes, so reload for accurate positions.
    await reload();
  }

  async function moveTask({
    taskId,
    fromStatus,
    toStatus,
    toIndex,
    nextTasks,
  }) {
    const previous = tasks;
    setTasks(nextTasks);
    try {
      const res = await api.post("/api/tasks/move", {
        taskId,
        fromStatus,
        toStatus,
        toIndex,
      });
      setTasks(res.data);
    } catch (err) {
      setTasks(previous);
      setError(getApiErrorMessage(err));
    }
  }

  useEffect(() => {
    reload();
  }, []);

  useEffect(() => {
    const onKeyDown = (e) => {
      const isK = String(e.key || "").toLowerCase() === "k";
      if (!isK) return;
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault();
      setPaletteOpen(true);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const detailsTask = useMemo(() => {
    if (!detailsTaskId) return null;
    return tasks.find((t) => t.id === detailsTaskId) || null;
  }, [tasks, detailsTaskId]);

  return (
    <div className="boardPage">
      <ThemeToggle />
      <OfflineBanner />
      <header className="topbar">
        <h1>Task Board</h1>
        <div className="topbarActions">
          <Link className="secondary" to="/analytics">
            Analytics
          </Link>
          <Link className="secondary" to="/calendar">
            Calendar
          </Link>
          <Link className="secondary" to="/timeline">
            Timeline
          </Link>
          <button
            type="button"
            className="secondary"
            onClick={() => setFocusMode((v) => !v)}
            title="Pick up to 3 tasks for today and hide the rest"
          >
            {focusMode ? "Focus: On" : "Focus: Off"}
          </button>
          <DropdownMenu trigger="More">
            <Link to="/archived">Archived</Link>
            <button type="button" onClick={() => setShowArchived((v) => !v)}>
              {showArchived ? "Hide archived tasks" : "Show archived tasks"}
            </button>
            <button
              type="button"
              onClick={() => setLayoutMode((v) => !v)}
              title="Rearrange and resize columns"
            >
              {layoutMode ? "Done" : "Customize"}
            </button>
            {me?.role === "ADMIN" ? <Link to="/admin">Admin</Link> : null}
            <button
              onClick={() => {
                clearToken();
                window.location.href = "/login";
              }}
            >
              Logout
            </button>
          </DropdownMenu>
        </div>
      </header>

      {loading ? <LoadingSpinner message="Loading tasks..." /> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading ? (
        <FilterBar
          tasks={tasks}
          filters={filters}
          onChange={setFilters}
          title="Filter tasks"
        />
      ) : null}

      {!loading ? (
        <div className="inlineRow wipRow">
          <span className="muted small">WIP limits:</span>
          <label
            className="muted small"
            style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}
          >
            TODO
            <input
              className="input"
              type="number"
              min={0}
              placeholder="∞"
              value={wipLimits.TODO ?? ""}
              onChange={(e) => {
                const raw = e.target.value;
                const n = raw === "" ? null : Math.floor(Number(raw));
                setWipLimits((prev) => ({
                  ...prev,
                  TODO: n != null && Number.isFinite(n) && n > 0 ? n : null,
                }));
              }}
              style={{ width: 90 }}
            />
          </label>
          <label
            className="muted small"
            style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}
          >
            IN_PROGRESS
            <input
              className="input"
              type="number"
              min={0}
              placeholder="∞"
              value={wipLimits.IN_PROGRESS ?? ""}
              onChange={(e) => {
                const raw = e.target.value;
                const n = raw === "" ? null : Math.floor(Number(raw));
                setWipLimits((prev) => ({
                  ...prev,
                  IN_PROGRESS:
                    n != null && Number.isFinite(n) && n > 0 ? n : null,
                }));
              }}
              style={{ width: 110 }}
            />
          </label>
          <button
            type="button"
            className="secondary"
            onClick={() =>
              setWipLimits({ TODO: null, IN_PROGRESS: null, DONE: null })
            }
            title="Clear WIP limits"
          >
            Clear
          </button>
        </div>
      ) : null}

      {focusMode ? (
        <section className="analyticsSplit" style={{ marginBottom: "1rem" }}>
          <div className="analyticsPanel">
            <h2>Today’s focus</h2>
            <div className="muted small">
              Pick up to 3 tasks. Only these show on the board.
            </div>
            {focusTasks.length ? (
              <ul className="analyticsList">
                {focusTasks.slice(0, 3).map((t) => (
                  <li key={t.id}>
                    <button
                      type="button"
                      className="analyticsListItem"
                      onClick={() => setDetailsTaskId(t.id)}
                      title="Open task details"
                    >
                      <span className="analyticsListTitle">{t.title}</span>
                      <div className="muted small">
                        {t.status} • {t.priority}
                        {t.dueDate ? ` • due ${String(t.dueDate)}` : ""}
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="muted" style={{ marginTop: "0.6rem" }}>
                No focus tasks yet. Open a task and toggle “Today’s focus”.
              </div>
            )}
          </div>

          <div className="analyticsPanel">
            <h2>Pomodoro</h2>
            <div className="muted small" style={{ marginBottom: "0.5rem" }}>
              Start a 25-minute session and log time.
            </div>

            <div className="inlineRow">
              <select
                className="select"
                value={pomodoroTaskId || ""}
                onChange={(e) => setPomodoroTaskId(e.target.value || null)}
                disabled={!focusTasks.length}
                title="Task to log time against"
              >
                <option value="">Select focus task…</option>
                {focusTasks.slice(0, 3).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.title}
                  </option>
                ))}
              </select>
              <span className="pill" title="Time remaining">
                {formatMmSs(pomodoroSecondsLeft)}
              </span>
            </div>

            <div className="inlineRow">
              <button
                type="button"
                className="secondary"
                disabled={!pomodoroTaskId || pomodoroRunning}
                onClick={async () => {
                  if (!pomodoroTaskId) return;
                  setPomodoroStatus("");
                  setPomodoroRunning(true);
                  setPomodoroEndsAt(Date.now() + pomodoroSecondsLeft * 1000);
                  try {
                    await api.post(`/api/tasks/${pomodoroTaskId}/timer/start`);
                  } catch {
                    // ignore
                  }
                }}
              >
                Start
              </button>
              <button
                type="button"
                className="secondary"
                disabled={!pomodoroTaskId || !pomodoroRunning}
                onClick={async () => {
                  if (!pomodoroTaskId) return;
                  setPomodoroRunning(false);
                  setPomodoroEndsAt(null);
                  try {
                    const res = await api.post(
                      `/api/tasks/${pomodoroTaskId}/timer/stop`,
                      { note: "Pomodoro paused" }
                    );
                    setTasks((prev) =>
                      prev.map((t) => (t.id === pomodoroTaskId ? res.data : t))
                    );
                  } catch {
                    // ignore
                  }
                }}
              >
                Pause
              </button>
              <button
                type="button"
                className="secondary"
                disabled={!pomodoroTaskId}
                onClick={async () => {
                  if (!pomodoroTaskId) return;
                  setPomodoroRunning(false);
                  setPomodoroEndsAt(null);
                  setPomodoroSecondsLeft(25 * 60);
                  setPomodoroStatus("");
                  try {
                    const res = await api.post(
                      `/api/tasks/${pomodoroTaskId}/timer/stop`,
                      { note: "Pomodoro stopped" }
                    );
                    setTasks((prev) =>
                      prev.map((t) => (t.id === pomodoroTaskId ? res.data : t))
                    );
                  } catch {
                    // ignore
                  }
                }}
              >
                Reset
              </button>
            </div>

            {pomodoroStatus ? (
              <div className="muted">{pomodoroStatus}</div>
            ) : null}
          </div>
        </section>
      ) : null}

      {selectedTaskIds.size ? (
        <section className="bulkBar">
          <div className="bulkLeft">
            <span className="pill">Selected: {selectedTaskIds.size}</span>
            <button
              type="button"
              className="secondary"
              onClick={() => setSelectedTaskIds(new Set())}
            >
              Clear
            </button>
          </div>

          <div className="bulkRight">
            <select
              className="select"
              value={bulkStatus}
              onChange={(e) => setBulkStatus(e.target.value)}
              title="Bulk move"
            >
              <option value="TODO">TODO</option>
              <option value="IN_PROGRESS">IN PROGRESS</option>
              <option value="DONE">DONE</option>
            </select>
            <button
              type="button"
              className="secondary"
              onClick={() => bulkAction("SET_STATUS", { status: bulkStatus })}
              title="Move selected tasks to a column"
            >
              Move
            </button>

            <button
              type="button"
              className="danger"
              onClick={() => bulkAction("DELETE")}
              title="Delete selected tasks"
            >
              Delete
            </button>

            <select
              className="select"
              value={bulkPriority}
              onChange={(e) => setBulkPriority(e.target.value)}
              title="Bulk priority"
            >
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
            <button
              type="button"
              onClick={() =>
                bulkAction("SET_PRIORITY", { priority: bulkPriority })
              }
            >
              Apply
            </button>

            <input
              className="input"
              placeholder="Label…"
              value={bulkLabel}
              onChange={(e) => setBulkLabel(e.target.value)}
              maxLength={24}
            />
            <button
              type="button"
              className="secondary"
              disabled={!bulkLabel.trim()}
              onClick={() => bulkAction("ADD_LABEL", { label: bulkLabel })}
            >
              Add label
            </button>
            <button
              type="button"
              className="secondary"
              disabled={!bulkLabel.trim()}
              onClick={() => bulkAction("REMOVE_LABEL", { label: bulkLabel })}
            >
              Remove label
            </button>
          </div>
        </section>
      ) : null}

      {!loading && overdueTasks.length ? (
        <section className="pendingPanel">
          <div className="pendingHeader">
            <div>
              <h2>Pending (Overdue)</h2>
              <div className="muted">These tasks passed their deadline</div>
            </div>
            <span className="pill">{overdueTasks.length}</span>
          </div>

          <div className="pendingList">
            {overdueTasks.slice(0, 8).map((t) => (
              <div key={t.id} className="pendingRow">
                <div className="pendingMain">
                  <strong className="pendingTitle">{t.title}</strong>
                  <div className="muted small">
                    Due: <strong>{String(t.dueDate)}</strong> • Status:{" "}
                    <strong>{t.status}</strong>
                  </div>
                </div>
                <div className="pendingActions">
                  <button
                    type="button"
                    className="mini"
                    onClick={() => quickMove(t.id, t.status, "TODO")}
                  >
                    ☐
                  </button>
                  <button
                    type="button"
                    className="mini"
                    onClick={() => quickMove(t.id, t.status, "IN_PROGRESS")}
                  >
                    ▶
                  </button>
                  <button
                    type="button"
                    className="mini"
                    onClick={() => quickMove(t.id, t.status, "DONE")}
                  >
                    ✓
                  </button>
                </div>
              </div>
            ))}
            {overdueTasks.length > 8 ? (
              <div className="muted small">
                Showing 8 of {overdueTasks.length} overdue tasks
              </div>
            ) : null}
          </div>
        </section>
      ) : null}

      {!loading ? (
        <KanbanBoard
          tasksByStatus={tasksByStatus}
          onCreateTask={createTask}
          onDeleteTask={deleteTask}
          onMoveTask={moveTask}
          onUpdatePriority={updateTaskPriority}
          layoutMode={layoutMode}
          wipLimits={wipLimits}
          columnOrder={columnOrder}
          onColumnOrderChange={setColumnOrder}
          onOpenDetails={(task) => setDetailsTaskId(task?.id || null)}
          selectedTaskIds={selectedTaskIds}
          onToggleTaskSelected={toggleSelected}
        />
      ) : null}

      <TaskDetailsModal
        open={Boolean(detailsTaskId)}
        task={detailsTask}
        allTasks={tasks}
        onClose={() => setDetailsTaskId(null)}
        onAddComment={addComment}
        onUpdateArchived={updateArchived}
        onUpdateLabels={updateLabels}
        onUpdateDependencies={updateDependencies}
        onAddChecklistItem={addChecklistItem}
        onUpdateChecklistItem={updateChecklistItem}
        onUpdateFocus={updateFocus}
        onUpdateTimeBudget={updateTimeBudget}
        onStartTimer={startTimer}
        onStopTimer={stopTimer}
        onAddDecision={addDecision}
        onUpdateRecurrence={updateRecurrence}
      />

      <CommandPalette
        open={paletteOpen}
        tasks={tasks}
        onClose={() => setPaletteOpen(false)}
        onOpenTask={(t) => {
          setPaletteOpen(false);
          setDetailsTaskId(t?.id || null);
        }}
      />
    </div>
  );
}
