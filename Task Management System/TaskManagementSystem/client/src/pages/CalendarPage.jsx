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

function startOfDay(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

function addMonths(d, n) {
  const x = new Date(d);
  x.setMonth(x.getMonth() + n);
  return x;
}

export default function CalendarPage() {
  const { me } = useMe();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detailsTaskId, setDetailsTaskId] = useState(null);

  const [filters, setFilters] = useStoredTaskFilters("calendar:filters:v1");

  const [month, setMonth] = useState(() => {
    const d = new Date();
    d.setDate(1);
    return startOfDay(d);
  });

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

  const tasksByDue = useMemo(() => {
    const map = new Map();
    for (const t of filteredTasks) {
      if (!t.dueDate) continue;
      const key = String(t.dueDate);
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(t);
    }
    for (const [k, arr] of map.entries()) {
      arr.sort((a, b) => String(a.status).localeCompare(String(b.status)));
      map.set(k, arr);
    }
    return map;
  }, [filteredTasks]);

  const gridDays = useMemo(() => {
    const first = new Date(month);
    first.setDate(1);
    const start = new Date(first);
    // Monday-first calendar (Mon=1)
    const dow = start.getDay(); // Sun=0
    const offset = (dow + 6) % 7; // Mon=0
    start.setDate(start.getDate() - offset);

    const out = [];
    for (let i = 0; i < 42; i++) {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      out.push(startOfDay(d));
    }
    return out;
  }, [month]);

  const monthLabel = month.toLocaleString(undefined, {
    month: "long",
    year: "numeric",
  });

  return (
    <div className="boardPage">
      <ThemeToggle />
      <OfflineBanner />
      <header className="topbar">
        <div>
          <h1>Calendar</h1>
          <div className="muted">Due dates across the month</div>
        </div>
        <div className="topbarActions">
          <Link className="secondary" to="/board">
            Board
          </Link>
          <Link className="secondary" to="/archived">
            Archived
          </Link>
          <Link className="secondary" to="/timeline">
            Timeline
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

      {loading ? <LoadingSpinner message="Loading calendar..." /> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading ? (
        <FilterBar
          tasks={tasks}
          filters={filters}
          onChange={setFilters}
          title="Filter calendar"
        />
      ) : null}

      <section className="calendarHeader">
        <button
          type="button"
          className="secondary"
          onClick={() => setMonth((m) => addMonths(m, -1))}
        >
          ◀
        </button>
        <strong>{monthLabel}</strong>
        <button
          type="button"
          className="secondary"
          onClick={() => setMonth((m) => addMonths(m, 1))}
        >
          ▶
        </button>
      </section>

      <section className="calendarGrid">
        {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => (
          <div key={d} className="calendarDow muted small">
            {d}
          </div>
        ))}

        {gridDays.map((d) => {
          const inMonth = d.getMonth() === month.getMonth();
          const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(
            2,
            "0"
          )}-${String(d.getDate()).padStart(2, "0")}`;
          const dayTasks = tasksByDue.get(key) || [];
          return (
            <div
              key={key}
              className={`calendarCell ${inMonth ? "" : "mutedCell"}`}
            >
              <div className="calendarDay">{d.getDate()}</div>
              <div className="calendarItems">
                {dayTasks.slice(0, 4).map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    className="calendarTask"
                    onClick={() => setDetailsTaskId(t.id)}
                    title={t.title}
                  >
                    <span className="calendarTaskTitle">{t.title}</span>
                    <span className="pill">{t.status}</span>
                  </button>
                ))}
                {dayTasks.length > 4 ? (
                  <div className="muted small">+{dayTasks.length - 4} more</div>
                ) : null}
              </div>
            </div>
          );
        })}
      </section>

      <TaskDetailsModal
        open={Boolean(detailsTaskId)}
        task={detailsTask}
        allTasks={tasks}
        onClose={() => setDetailsTaskId(null)}
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
