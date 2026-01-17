import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { getApiErrorMessage } from "../lib/apiClient";
import { clearToken } from "../lib/auth";
import { useMe } from "../lib/me";
import { fetchTasks, useTaskMutations } from "../lib/tasksClient";
import TaskDetailsModal from "../ui/TaskDetailsModal";
import { ThemeToggle } from "../components/ThemeToggle";
import OfflineBanner from "../components/OfflineBanner";
import FilterBar from "../components/FilterBar";
import LoadingSpinner from "../components/LoadingSpinner";
import { useFilteredTasks, useStoredTaskFilters } from "../lib/taskFilters";

function formatDateTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString();
}

export default function ArchivedPage() {
  const { me } = useMe();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detailsTaskId, setDetailsTaskId] = useState(null);

  const [filters, setFilters] = useStoredTaskFilters("archived:filters:v1");

  async function reload() {
    setError("");
    setLoading(true);
    try {
      const data = await fetchTasks();
      setTasks(data);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reload();
  }, []);

  const detailsTask = useMemo(() => {
    if (!detailsTaskId) return null;
    return tasks.find((t) => t.id === detailsTaskId) || null;
  }, [tasks, detailsTaskId]);

  const archivedTasks = useMemo(() => {
    return tasks.filter((t) => Boolean(t?.archived));
  }, [tasks]);

  const filteredArchivedTasks = useFilteredTasks(archivedTasks, filters);

  const grouped = useMemo(() => {
    const by = { TODO: [], IN_PROGRESS: [], DONE: [], OTHER: [] };
    for (const t of filteredArchivedTasks) {
      const s = String(t?.status || "");
      if (s === "TODO") by.TODO.push(t);
      else if (s === "IN_PROGRESS") by.IN_PROGRESS.push(t);
      else if (s === "DONE") by.DONE.push(t);
      else by.OTHER.push(t);
    }

    const timeKey = (t) => {
      const iso = t?.archivedAt || t?.updatedAt || t?.createdAt;
      if (!iso) return 0;
      const ms = new Date(iso).getTime();
      return Number.isFinite(ms) ? ms : 0;
    };

    const sort = (a, b) => {
      const ap = a?.pinned ? 1 : 0;
      const bp = b?.pinned ? 1 : 0;
      if (bp !== ap) return bp - ap;
      const at = timeKey(a);
      const bt = timeKey(b);
      if (bt !== at) return bt - at;
      return String(a?.title || "").localeCompare(String(b?.title || ""));
    };

    for (const k of Object.keys(by)) {
      by[k].sort(sort);
    }

    return by;
  }, [filteredArchivedTasks]);

  const {
    addComment,
    updateLabels,
    updateArchived,
    addChecklistItem,
    updateChecklistItem,
    updateFocus,
    updateTimeBudget,
    startTimer,
    stopTimer,
    addDecision,
    updateRecurrence,
    updateDependencies,
  } = useTaskMutations({
    setTasks,
    setError,
    onUnarchivedTaskId: (taskId) => {
      if (detailsTaskId && String(detailsTaskId) === String(taskId)) {
        setDetailsTaskId(null);
      }
    },
  });

  const totalArchived = archivedTasks.length;

  function Section({ title, list }) {
    if (!list.length) return null;
    return (
      <div className="timelineGroup">
        <div className="timelineDate">
          <strong>{title}</strong>
          <span className="pill">{list.length}</span>
        </div>
        <div className="timelineList">
          {list.map((t) => (
            <div key={t.id} className="timelineItemRow">
              <button
                type="button"
                className="timelineItem"
                onClick={() => setDetailsTaskId(t.id)}
                title="Open details"
              >
                <div>
                  <strong>{t.title}</strong>
                  <div className="muted small">
                    {t.status} • {t.priority}
                    {t.dueDate ? ` • Due ${t.dueDate}` : ""}
                    {t.archivedAt
                      ? ` • Archived ${formatDateTime(t.archivedAt)}`
                      : ""}
                    {t.checklistTotal
                      ? ` • ☑ ${t.checklistDone}/${t.checklistTotal}`
                      : ""}
                  </div>
                </div>
                <div className="timelineLabels">
                  {(Array.isArray(t.labels) ? t.labels : [])
                    .slice(0, 3)
                    .map((l) => (
                      <span key={l} className="labelChip small">
                        {l}
                      </span>
                    ))}
                </div>
              </button>
              <button
                type="button"
                className="secondary timelineAction"
                title="Move back to active tasks"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  updateArchived(t.id, false);
                }}
              >
                Unarchive
              </button>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="boardPage">
      <ThemeToggle />
      <OfflineBanner />

      <header className="topbar">
        <div>
          <h1>Archived</h1>
          <div className="muted">Hidden tasks (manual + auto-archived)</div>
        </div>
        <div className="topbarActions">
          <Link className="secondary" to="/board">
            Board
          </Link>
          <Link className="secondary" to="/calendar">
            Calendar
          </Link>
          <Link className="secondary" to="/timeline">
            Timeline
          </Link>
          <Link className="secondary" to="/analytics">
            Analytics
          </Link>
          {me?.role === "ADMIN" ? (
            <Link className="secondary" to="/admin">
              Admin
            </Link>
          ) : null}
          <button
            onClick={() => {
              clearToken();
              window.location.href = "/login";
            }}
            className="secondary"
          >
            Logout
          </button>
        </div>
      </header>

      {loading ? <LoadingSpinner message="Loading archived tasks..." /> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading ? (
        <FilterBar
          tasks={archivedTasks}
          filters={filters}
          onChange={setFilters}
          title={`Filter archived (${totalArchived})`}
        />
      ) : null}

      {!loading && !filteredArchivedTasks.length ? (
        <div className="muted" style={{ padding: "0.75rem" }}>
          No archived tasks match your filters.
        </div>
      ) : null}

      <section className="timeline">
        <Section title="TODO" list={grouped.TODO} />
        <Section title="IN PROGRESS" list={grouped.IN_PROGRESS} />
        <Section title="DONE" list={grouped.DONE} />
        <Section title="OTHER" list={grouped.OTHER} />
      </section>

      <TaskDetailsModal
        open={Boolean(detailsTaskId)}
        task={detailsTask}
        allTasks={tasks}
        onClose={() => setDetailsTaskId(null)}
        onUpdateArchived={updateArchived}
        onAddComment={addComment}
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
    </div>
  );
}
