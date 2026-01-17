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

export default function TimelinePage() {
  const { me } = useMe();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detailsTaskId, setDetailsTaskId] = useState(null);

  const [filters, setFilters] = useStoredTaskFilters("timeline:filters:v1");

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

  const filteredTasks = useFilteredTasks(tasks, filters);

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
  } = useTaskMutations({ setTasks, setError });

  const grouped = useMemo(() => {
    const withDue = [];
    const withoutDue = [];
    for (const t of filteredTasks) {
      if (t.dueDate) withDue.push(t);
      else withoutDue.push(t);
    }
    withDue.sort((a, b) => String(a.dueDate).localeCompare(String(b.dueDate)));

    const groups = new Map();
    for (const t of withDue) {
      const k = String(t.dueDate);
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k).push(t);
    }
    return { groups, withoutDue };
  }, [filteredTasks]);

  return (
    <div className="boardPage">
      <ThemeToggle />
      <OfflineBanner />
      <header className="topbar">
        <div>
          <h1>Timeline</h1>
          <div className="muted">Tasks ordered by due date</div>
        </div>
        <div className="topbarActions">
          <Link className="secondary" to="/board">
            Board
          </Link>
          <Link className="secondary" to="/calendar">
            Calendar
          </Link>
          <Link className="secondary" to="/archived">
            Archived
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

      {loading ? <LoadingSpinner message="Loading timeline..." /> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading ? (
        <FilterBar
          tasks={tasks}
          filters={filters}
          onChange={setFilters}
          title="Filter timeline"
        />
      ) : null}

      <section className="timeline">
        {[...grouped.groups.entries()].map(([date, list]) => (
          <div key={date} className="timelineGroup">
            <div className="timelineDate">
              <strong>{date}</strong>
              <span className="pill">{list.length}</span>
            </div>
            <div className="timelineList">
              {list.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  className="timelineItem"
                  onClick={() => setDetailsTaskId(t.id)}
                >
                  <div>
                    <strong>{t.title}</strong>
                    <div className="muted small">
                      {t.status} • {t.priority}
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
              ))}
            </div>
          </div>
        ))}

        {grouped.withoutDue.length ? (
          <div className="timelineGroup">
            <div className="timelineDate">
              <strong>No due date</strong>
              <span className="pill">{grouped.withoutDue.length}</span>
            </div>
            <div className="timelineList">
              {grouped.withoutDue.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  className="timelineItem"
                  onClick={() => setDetailsTaskId(t.id)}
                >
                  <div>
                    <strong>{t.title}</strong>
                    <div className="muted small">
                      {t.status} • {t.priority}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </section>

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
    </div>
  );
}
