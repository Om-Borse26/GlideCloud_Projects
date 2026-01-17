import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, getApiErrorMessage } from "../lib/apiClient";
import { clearToken } from "../lib/auth";
import { useMe } from "../lib/me";
import { useTaskDetailsMutations } from "../lib/tasksClient";
import { ThemeToggle } from "../components/ThemeToggle";
import LoadingSpinner from "../components/LoadingSpinner";
import TaskDetailsModal from "../ui/TaskDetailsModal";

function formatMinutes(mins) {
  const m = Number(mins || 0);
  if (!Number.isFinite(m) || m <= 0) return "0m";
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (!h) return `${r}m`;
  if (!r) return `${h}h`;
  return `${h}h ${r}m`;
}

function formatHours(hours) {
  if (hours == null) return "—";
  const h = Number(hours);
  if (!Number.isFinite(h)) return "—";
  return `${h.toFixed(h < 10 ? 1 : 0)}h`;
}

export default function AnalyticsPage() {
  const { me } = useMe();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [detailsTask, setDetailsTask] = useState(null);

  async function reload() {
    setError("");
    setLoading(true);
    try {
      const res = await api.get("/api/analytics/overview?days=14");
      setData(res.data);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reload();
  }, []);

  async function openTask(taskId) {
    if (!taskId) return;
    setError("");
    try {
      const res = await api.get(`/api/tasks/${taskId}`);
      setDetailsTask(res.data);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

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
  } = useTaskDetailsMutations({
    setTask: setDetailsTask,
    setError,
    afterUpdate: reload,
  });

  const trendMax = useMemo(() => {
    const arr = data?.trend || [];
    let max = 0;
    for (const p of arr) {
      max = Math.max(max, Number(p?.completedCount || 0));
    }
    return max;
  }, [data]);

  return (
    <div className="boardPage">
      <ThemeToggle />
      <header className="topbar">
        <div>
          <h1>Analytics</h1>
          <div className="muted">Daily overview + recent trends</div>
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

      {loading ? <LoadingSpinner message="Loading analytics..." /> : null}
      {error ? <div className="error">{error}</div> : null}

      {!loading && data ? (
        <>
          <section className="analyticsGrid">
            <div className="analyticsCard">
              <div className="muted small">Open tasks</div>
              <div className="analyticsValue">{data.openTasks}</div>
              <div className="muted small">Focus: {data.focusOpenTasks}</div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Overdue</div>
              <div className="analyticsValue dangerText">
                {data.overdueOpenTasks}
              </div>
              <div className="muted small">
                Due today: {data.dueTodayOpenTasks}
              </div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Completed</div>
              <div className="analyticsValue">{data.completedToday}</div>
              <div className="muted small">
                This week: {data.completedThisWeek}
              </div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Completion streak</div>
              <div className="analyticsValue">
                {data.completionStreakDays || 0}
              </div>
              <div className="muted small">days in a row</div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Time logged</div>
              <div className="analyticsValue">
                {formatMinutes(data.loggedMinutesToday)}
              </div>
              <div className="muted small">
                This week: {formatMinutes(data.loggedMinutesThisWeek)}
              </div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Avg cycle time (30d)</div>
              <div className="analyticsValue">
                {formatHours(data.avgCycleTimeHoursLast30Days)}
              </div>
              <div className="muted small">
                Completed (30d): {data.completedLast30Days}
              </div>
            </div>

            <div className="analyticsCard">
              <div className="muted small">Upcoming (7d)</div>
              <div className="analyticsValue">
                {data.upcoming7DaysOpenTasks}
              </div>
              <div className="muted small">
                Due tomorrow: {data.dueTomorrowOpenTasks}
              </div>
            </div>
          </section>

          <section className="analyticsSplit">
            <div className="analyticsPanel">
              <h2>Overdue (top)</h2>
              {data.overdueTop?.length ? (
                <ul className="analyticsList">
                  {data.overdueTop.map((t) => (
                    <li key={t.id}>
                      <button
                        type="button"
                        className="analyticsListItem"
                        onClick={() => openTask(t.id)}
                        title="Open task details"
                      >
                        <span className="analyticsListTitle">{t.title}</span>
                        <span className="muted small">
                          {t.priority ? `${t.priority} · ` : ""}
                          due {t.dueDate}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="muted">No overdue tasks.</div>
              )}

              <h2 style={{ marginTop: "1rem" }}>Due today (top)</h2>
              {data.dueTodayTop?.length ? (
                <ul className="analyticsList">
                  {data.dueTodayTop.map((t) => (
                    <li key={t.id}>
                      <button
                        type="button"
                        className="analyticsListItem"
                        onClick={() => openTask(t.id)}
                        title="Open task details"
                      >
                        <span className="analyticsListTitle">{t.title}</span>
                        <span className="muted small">
                          {t.priority ? `${t.priority}` : ""}
                          {t.focus ? " · Focus" : ""}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="muted">Nothing due today.</div>
              )}
            </div>

            <div className="analyticsPanel">
              <h2>Trend (last 14 days)</h2>
              <div className="muted small" style={{ marginBottom: "0.5rem" }}>
                Completions + time logged per day
              </div>
              <div className="trendList">
                {(data.trend || []).map((p) => {
                  const c = Number(p?.completedCount || 0);
                  const w = trendMax ? Math.round((c / trendMax) * 100) : 0;
                  return (
                    <div key={p.date} className="trendRow">
                      <div className="trendDate muted small">{p.date}</div>
                      <div className="trendBarWrap">
                        <div className="trendBar" style={{ width: `${w}%` }} />
                      </div>
                      <div className="trendNums">
                        <span className="pill">{c} done</span>
                        <span className="pill">
                          {formatMinutes(p.loggedMinutes)} logged
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>

              {data.topOpenLabels?.length ? (
                <>
                  <h2 style={{ marginTop: "1rem" }}>Top labels (open)</h2>
                  <div className="labelRow">
                    {data.topOpenLabels.map((l) => (
                      <span key={l.label} className="labelChip">
                        {l.label} · {l.count}
                      </span>
                    ))}
                  </div>
                </>
              ) : null}

              {data.bottlenecks?.length ? (
                <>
                  <h2 style={{ marginTop: "1rem" }}>Bottlenecks</h2>
                  <div
                    className="muted small"
                    style={{ marginBottom: "0.5rem" }}
                  >
                    Where open work is aging the most
                  </div>
                  <div className="trendList">
                    {data.bottlenecks.map((b) => (
                      <div key={b.status} className="trendRow">
                        <div className="trendDate muted small">{b.status}</div>
                        <div className="trendBarWrap">
                          <div
                            className="trendBar"
                            style={{
                              width: `${Math.min(
                                100,
                                (Number(b.avgAgeDays || 0) / 14) * 100
                              )}%`,
                            }}
                          />
                        </div>
                        <div className="trendNums">
                          <span className="pill">{b.openCount} open</span>
                          <span className="pill">avg {b.avgAgeDays}d</span>
                          <span className="pill">
                            oldest {b.oldestAgeDays}d
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              ) : null}
            </div>
          </section>
        </>
      ) : null}

      <TaskDetailsModal
        open={Boolean(detailsTask)}
        task={detailsTask}
        onClose={() => setDetailsTask(null)}
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
