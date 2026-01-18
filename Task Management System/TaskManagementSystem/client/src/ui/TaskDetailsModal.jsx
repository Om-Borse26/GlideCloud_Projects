import { useEffect, useMemo, useRef, useState } from "react";

const EMPTY_ARRAY = [];

function formatDateTime(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString();
}

function activityLabel(a) {
  const t = String(a?.type || "").toUpperCase();
  switch (t) {
    case "CREATED":
      return "Created";
    case "UPDATED":
      return "Updated";
    case "MOVED":
      return "Moved";
    case "REORDERED":
      return "Reordered";
    case "COMMENTED":
      return "Commented";
    case "ASSIGNED":
      return "Assigned";
    case "DEPENDENCIES_UPDATED":
      return "Dependencies updated";
    default:
      return t || "Activity";
  }
}

function normalizeLabel(text) {
  return String(text || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 24);
}

export default function TaskDetailsModal({
  open,
  task,
  allTasks,
  onClose,
  onAddComment,
  onUpdateArchived,
  onUpdateLabels,
  onUpdateDependencies,
  onAddChecklistItem,
  onUpdateChecklistItem,
  onUpdateFocus,
  onUpdateTimeBudget,
  onStartTimer,
  onStopTimer,
  onAddDecision,
  onUpdateRecurrence,
}) {
  const [busy, setBusy] = useState(false);
  const [taskActionError, setTaskActionError] = useState("");

  const [labelInput, setLabelInput] = useState("");
  const [depQuery, setDepQuery] = useState("");
  const [depPickId, setDepPickId] = useState("");
  const [checklistInput, setChecklistInput] = useState("");
  const [commentInput, setCommentInput] = useState("");
  const [decisionInput, setDecisionInput] = useState("");
  const [timeBudgetInput, setTimeBudgetInput] = useState("");
  const firstInputRef = useRef(null);

  const [recurrenceDraft, setRecurrenceDraft] = useState(() => ({
    frequency: null,
    interval: "",
    weekdaysOnly: false,
    daysOfWeek: [],
    nthBusinessDayOfMonth: null,
    endDate: null,
  }));

  const allTasksList = useMemo(() => {
    return Array.isArray(allTasks) ? allTasks : EMPTY_ARRAY;
  }, [allTasks]);
  const taskId = task?.id ? String(task.id) : "";
  const labels = Array.isArray(task?.labels) ? task.labels : [];
  const blockedByTaskIds = useMemo(() => {
    return Array.isArray(task?.blockedByTaskIds)
      ? task.blockedByTaskIds
      : EMPTY_ARRAY;
  }, [task?.blockedByTaskIds]);
  const checklist = Array.isArray(task?.checklist) ? task.checklist : [];
  const comments = Array.isArray(task?.comments) ? task.comments : [];
  const decisions = Array.isArray(task?.decisions) ? task.decisions : [];
  const activity = Array.isArray(task?.activity) ? task.activity : [];
  const recurrence = task?.recurrence || null;

  const timeBudgetMinutes =
    typeof task?.timeBudgetMinutes === "number" ? task.timeBudgetMinutes : null;
  const totalLoggedMinutes = Number(task?.totalLoggedMinutes || 0);
  const activeTimerStartedAt = task?.activeTimerStartedAt || null;
  const isTimerRunning = Boolean(activeTimerStartedAt);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => {
      if (e.key === "Escape") onClose?.();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => firstInputRef.current?.focus?.(), 0);
    return () => clearTimeout(t);
  }, [open, taskId]);

  useEffect(() => {
    if (!open) return;
    setTaskActionError("");
    setLabelInput("");
    setDepQuery("");
    setDepPickId("");
    setChecklistInput("");
    setCommentInput("");
    setDecisionInput("");
    setTimeBudgetInput(
      timeBudgetMinutes == null ? "" : String(timeBudgetMinutes),
    );
    const r = task?.recurrence || null;
    setRecurrenceDraft(() => ({
      frequency: r?.frequency || null,
      interval: r?.interval == null ? "" : String(r.interval),
      weekdaysOnly: Boolean(r?.weekdaysOnly),
      daysOfWeek: Array.isArray(r?.daysOfWeek) ? r.daysOfWeek : [],
      nthBusinessDayOfMonth:
        typeof r?.nthBusinessDayOfMonth === "number"
          ? r.nthBusinessDayOfMonth
          : null,
      endDate: r?.endDate || null,
    }));
  }, [open, taskId, timeBudgetMinutes, task?.recurrence]);

  const dependencyOptions = useMemo(() => {
    if (!taskId) return [];
    const q = depQuery.trim().toLowerCase();
    const selected = new Set(blockedByTaskIds);
    return allTasksList
      .filter((t) => t?.id && String(t.id) !== taskId)
      .filter((t) => !selected.has(t.id))
      .filter((t) => {
        if (!q) return true;
        const title = String(t.title || "").toLowerCase();
        return title.includes(q) || String(t.id).toLowerCase().includes(q);
      })
      .slice(0, 50);
  }, [allTasksList, blockedByTaskIds, depQuery, taskId]);

  const blockers = useMemo(() => {
    if (!blockedByTaskIds.length) return [];
    const byId = new Map(allTasksList.map((t) => [t.id, t]));
    return blockedByTaskIds
      .map((id) => byId.get(id) || { id, title: id, status: "UNKNOWN" })
      .filter(Boolean);
  }, [blockedByTaskIds, allTasksList]);

  const openBlockersCount = useMemo(() => {
    return blockers.filter((b) => String(b.status) !== "DONE").length;
  }, [blockers]);

  const overBudget =
    typeof timeBudgetMinutes === "number" &&
    timeBudgetMinutes > 0 &&
    totalLoggedMinutes >= timeBudgetMinutes;

  const nearBudget =
    typeof timeBudgetMinutes === "number" &&
    timeBudgetMinutes > 0 &&
    totalLoggedMinutes >= Math.ceil(timeBudgetMinutes * 0.8) &&
    totalLoggedMinutes < timeBudgetMinutes;

  if (!open || !task) return null;

  const dueDate = task?.dueDate ? formatDateTime(task.dueDate) : "";

  async function safeRun(fn) {
    if (!fn) return;
    setBusy(true);
    try {
      setTaskActionError("");
      await fn();
    } catch (err) {
      setTaskActionError(String(err?.message || "Action failed"));
      throw err;
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="taskModalOverlay"
      role="dialog"
      aria-modal="true"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      <div className="taskModal">
        <div className="taskModalHeader">
          <div className="taskModalTitle">
            <h2>{task.title || "(untitled)"}</h2>
            <div className="taskModalMeta">
              <span className="pill">{String(task.status || "")}</span>
              <span className="pill">{String(task.priority || "")}</span>
              {dueDate ? <span className="pill">Due: {dueDate}</span> : null}
              {task.focus ? <span className="pill">Focus</span> : null}
              {task.archived ? <span className="pill">Archived</span> : null}
              {openBlockersCount ? (
                <span className="pill">Blocked by {openBlockersCount}</span>
              ) : null}
            </div>
          </div>

          <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
            {onUpdateArchived ? (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() =>
                  safeRun(() => onUpdateArchived(taskId, !task.archived))
                }
                title={task.archived ? "Unarchive task" : "Archive task"}
              >
                {task.archived ? "Unarchive" : "Archive"}
              </button>
            ) : null}

            <button type="button" className="secondary" onClick={onClose}>
              Close
            </button>
          </div>
        </div>

        <div className="taskModalBody">
          {taskActionError ? (
            <div className="error">{taskActionError}</div>
          ) : null}

          {task.description ? (
            <p className="taskModalDesc">{task.description}</p>
          ) : null}

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Labels</h3>
              <span className="pill">{labels.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {labels.length ? (
                <div className="labelRow">
                  {labels.map((l) => (
                    <span key={l} className="labelChip">
                      {l}
                      {onUpdateLabels ? (
                        <button
                          type="button"
                          className="labelChipX"
                          disabled={busy}
                          onClick={() =>
                            safeRun(() =>
                              onUpdateLabels(
                                taskId,
                                labels.filter((x) => x !== l),
                              ),
                            )
                          }
                          aria-label={`Remove label ${l}`}
                          title="Remove"
                        >
                          ×
                        </button>
                      ) : null}
                    </span>
                  ))}
                </div>
              ) : (
                <div className="muted">No labels</div>
              )}

              {onUpdateLabels ? (
                <form
                  className="inlineRow"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const next = normalizeLabel(labelInput);
                    if (!next) return;
                    if (labels.includes(next)) {
                      setLabelInput("");
                      return;
                    }
                    safeRun(async () => {
                      await onUpdateLabels(taskId, [...labels, next]);
                      setLabelInput("");
                    });
                  }}
                >
                  <input
                    ref={firstInputRef}
                    className="input"
                    placeholder="Add label…"
                    value={labelInput}
                    maxLength={24}
                    disabled={busy}
                    onChange={(e) => setLabelInput(e.target.value)}
                  />
                  <button type="submit" disabled={busy}>
                    Add
                  </button>
                </form>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Dependencies</h3>
              <span className="pill">{blockedByTaskIds.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {blockers.length ? (
                <div className="commentList">
                  {blockers.map((b) => (
                    <div key={b.id} className="commentItem">
                      <div className="commentHeader">
                        <strong>{b.title}</strong>
                        <span className="muted small">{String(b.status)}</span>
                      </div>
                      {onUpdateDependencies ? (
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy}
                          onClick={() =>
                            safeRun(() =>
                              onUpdateDependencies(
                                taskId,
                                blockedByTaskIds.filter((id) => id !== b.id),
                              ),
                            )
                          }
                        >
                          Remove
                        </button>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="muted">No dependencies</div>
              )}

              {onUpdateDependencies ? (
                <div className="commentComposer">
                  <input
                    className="input"
                    placeholder="Search tasks to block on…"
                    value={depQuery}
                    disabled={busy}
                    onChange={(e) => setDepQuery(e.target.value)}
                  />
                  <div className="inlineRow">
                    <select
                      className="select"
                      value={depPickId}
                      disabled={busy}
                      onChange={(e) => setDepPickId(e.target.value)}
                    >
                      <option value="">Pick dependency…</option>
                      {dependencyOptions.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.title}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      disabled={!depPickId || busy}
                      onClick={() =>
                        safeRun(async () => {
                          await onUpdateDependencies(taskId, [
                            ...blockedByTaskIds,
                            depPickId,
                          ]);
                          setDepPickId("");
                          setDepQuery("");
                        })
                      }
                    >
                      Add
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Focus & Time</h3>
              <span className="pill">
                {isTimerRunning ? "Running" : "Idle"}
              </span>
            </div>
            <div className="taskModalSectionBody">
              <div className="inlineRow">
                {onUpdateFocus ? (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() =>
                      safeRun(() => onUpdateFocus(taskId, !task.focus))
                    }
                  >
                    {task.focus ? "Unfocus" : "Mark focus"}
                  </button>
                ) : null}

                {onStartTimer && onStopTimer ? (
                  isTimerRunning ? (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() => safeRun(() => onStopTimer(taskId, null))}
                    >
                      Stop timer
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() => safeRun(() => onStartTimer(taskId))}
                    >
                      Start timer
                    </button>
                  )
                ) : null}
              </div>

              {activeTimerStartedAt ? (
                <div className="muted small">
                  Started: {formatDateTime(activeTimerStartedAt)}
                </div>
              ) : null}

              <div className="muted small">
                Logged: {totalLoggedMinutes} minutes
              </div>

              {onUpdateTimeBudget ? (
                <form
                  className="inlineRow"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const n =
                      timeBudgetInput === "" ? null : Number(timeBudgetInput);
                    safeRun(() => onUpdateTimeBudget(taskId, n));
                  }}
                >
                  <input
                    className="input"
                    inputMode="numeric"
                    placeholder="Time budget (minutes)"
                    value={timeBudgetInput}
                    disabled={busy}
                    onChange={(e) => setTimeBudgetInput(e.target.value)}
                  />
                  <button type="submit" disabled={busy}>
                    Save
                  </button>
                </form>
              ) : null}

              {overBudget ? (
                <div className="error">
                  You’re over the time budget for this task.
                </div>
              ) : nearBudget ? (
                <div className="muted">
                  You’re close to the time budget — consider scoping down.
                </div>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Recurrence</h3>
              <span className="pill">{recurrence ? "On" : "Off"}</span>
            </div>
            <div className="taskModalSectionBody">
              {recurrence ? (
                <div className="muted small">
                  {recurrence.frequency}
                  {recurrence.interval ? ` • every ${recurrence.interval}` : ""}
                  {recurrence.weekdaysOnly ? " • weekdays" : ""}
                  {recurrence.nthBusinessDayOfMonth
                    ? ` • ${recurrence.nthBusinessDayOfMonth}th business day`
                    : ""}
                  {recurrence.endDate ? ` • until ${recurrence.endDate}` : ""}
                </div>
              ) : (
                <div className="muted">Not recurring</div>
              )}

              {onUpdateRecurrence ? (
                <div className="recurrenceForm">
                  <div className="inlineRow">
                    <select
                      className="select"
                      value={recurrenceDraft.frequency || ""}
                      disabled={busy}
                      onChange={(e) => {
                        const v = e.target.value || null;
                        setRecurrenceDraft((prev) => ({
                          ...prev,
                          frequency: v,
                        }));
                      }}
                    >
                      <option value="">None</option>
                      <option value="DAILY">Daily</option>
                      <option value="WEEKLY">Weekly</option>
                      <option value="MONTHLY">Monthly</option>
                    </select>

                    {recurrenceDraft.frequency ? (
                      <>
                        <input
                          className="input"
                          style={{ width: 120 }}
                          inputMode="numeric"
                          placeholder="Interval"
                          value={recurrenceDraft.interval ?? ""}
                          disabled={busy}
                          onChange={(e) =>
                            setRecurrenceDraft((prev) => ({
                              ...prev,
                              interval: e.target.value,
                            }))
                          }
                        />

                        <label className="muted weekdaysOnlyToggle">
                          <input
                            type="checkbox"
                            checked={Boolean(recurrenceDraft.weekdaysOnly)}
                            disabled={busy}
                            onChange={(e) =>
                              setRecurrenceDraft((prev) => ({
                                ...prev,
                                weekdaysOnly: e.target.checked,
                              }))
                            }
                          />
                          Weekdays only
                        </label>
                      </>
                    ) : null}
                  </div>

                  {recurrenceDraft.frequency === "WEEKLY" ? (
                    <div className="recurrenceDays">
                      {[1, 2, 3, 4, 5, 6, 7].map((d) => {
                        const labels = [
                          "Mon",
                          "Tue",
                          "Wed",
                          "Thu",
                          "Fri",
                          "Sat",
                          "Sun",
                        ];
                        const days = Array.isArray(recurrenceDraft.daysOfWeek)
                          ? recurrenceDraft.daysOfWeek
                          : [];
                        const checked = days.includes(d);
                        return (
                          <label key={d} className="muted weekdaysOnlyToggle">
                            <input
                              type="checkbox"
                              checked={checked}
                              disabled={busy}
                              onChange={(e) => {
                                const next = e.target.checked
                                  ? [...days, d]
                                  : days.filter((x) => x !== d);
                                setRecurrenceDraft((prev) => ({
                                  ...prev,
                                  daysOfWeek: next.sort((a, b) => a - b),
                                }));
                              }}
                            />
                            {labels[d - 1]}
                          </label>
                        );
                      })}
                    </div>
                  ) : null}

                  {recurrenceDraft.frequency === "MONTHLY" ? (
                    <div className="inlineRow">
                      <input
                        className="input"
                        style={{ width: 240 }}
                        inputMode="numeric"
                        placeholder="Nth business day (e.g. 1)"
                        value={
                          recurrenceDraft.nthBusinessDayOfMonth == null
                            ? ""
                            : String(recurrenceDraft.nthBusinessDayOfMonth)
                        }
                        disabled={busy}
                        onChange={(e) =>
                          setRecurrenceDraft((prev) => ({
                            ...prev,
                            nthBusinessDayOfMonth:
                              e.target.value === ""
                                ? null
                                : Number(e.target.value),
                          }))
                        }
                      />
                    </div>
                  ) : null}

                  <div className="inlineRow">
                    {recurrenceDraft.frequency ? (
                      <input
                        className="input"
                        type="date"
                        value={recurrenceDraft.endDate || ""}
                        disabled={busy}
                        onChange={(e) =>
                          setRecurrenceDraft((prev) => ({
                            ...prev,
                            endDate: e.target.value || null,
                          }))
                        }
                      />
                    ) : null}

                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() =>
                        safeRun(async () => {
                          const payload = recurrenceDraft.frequency
                            ? {
                                frequency: recurrenceDraft.frequency,
                                interval:
                                  recurrenceDraft.interval == null ||
                                  recurrenceDraft.interval === ""
                                    ? 1
                                    : Number(recurrenceDraft.interval),
                                weekdaysOnly: Boolean(
                                  recurrenceDraft.weekdaysOnly,
                                ),
                                daysOfWeek:
                                  recurrenceDraft.frequency === "WEEKLY"
                                    ? Array.isArray(recurrenceDraft.daysOfWeek)
                                      ? recurrenceDraft.daysOfWeek
                                      : []
                                    : [],
                                endDate: recurrenceDraft.endDate || null,
                                nthBusinessDayOfMonth:
                                  recurrenceDraft.nthBusinessDayOfMonth == null
                                    ? null
                                    : Number(
                                        recurrenceDraft.nthBusinessDayOfMonth,
                                      ),
                              }
                            : { frequency: null };
                          await onUpdateRecurrence(taskId, payload);
                        })
                      }
                    >
                      Save recurrence
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Checklist</h3>
              <span className="pill">{checklist.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {checklist.length ? (
                <div className="checklistList">
                  {checklist.map((it) => (
                    <div key={it.id} className="checklistItem">
                      <input
                        type="checkbox"
                        checked={Boolean(it.done)}
                        disabled={busy || !onUpdateChecklistItem}
                        onChange={(e) =>
                          safeRun(() =>
                            onUpdateChecklistItem?.(taskId, it.id, {
                              done: e.target.checked,
                              text: it.text,
                            }),
                          )
                        }
                      />
                      <span
                        className={
                          it.done ? "checklistText done" : "checklistText"
                        }
                      >
                        {it.text}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="muted">No checklist items</div>
              )}

              {onAddChecklistItem ? (
                <form
                  className="inlineRow"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const text = String(checklistInput || "").trim();
                    if (!text) return;
                    safeRun(async () => {
                      await onAddChecklistItem(taskId, text);
                      setChecklistInput("");
                    });
                  }}
                >
                  <input
                    className="input"
                    placeholder="Add checklist item…"
                    value={checklistInput}
                    disabled={busy}
                    onChange={(e) => setChecklistInput(e.target.value)}
                  />
                  <button type="submit" disabled={busy}>
                    Add
                  </button>
                </form>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Decisions</h3>
              <span className="pill">{decisions.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {decisions.length ? (
                <div className="commentList">
                  {decisions
                    .slice()
                    .sort(
                      (a, b) =>
                        new Date(a.createdAt).getTime() -
                        new Date(b.createdAt).getTime(),
                    )
                    .map((d) => (
                      <div key={d.id} className="commentItem">
                        <div className="commentHeader">
                          <div className="commentHeaderLeft">
                            <strong className="commentAuthor">
                              {d.authorEmail || "User"}
                            </strong>
                            <span className="muted small">Decision</span>
                          </div>
                          <span className="muted small">
                            {formatDateTime(d.createdAt)}
                          </span>
                        </div>
                        <div className="commentMessage">{d.message}</div>
                      </div>
                    ))}
                </div>
              ) : (
                <div className="muted">No decisions yet</div>
              )}

              {onAddDecision ? (
                <form
                  className="inlineRow"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const msg = String(decisionInput || "").trim();
                    if (!msg) return;
                    safeRun(async () => {
                      await onAddDecision(taskId, msg);
                      setDecisionInput("");
                    });
                  }}
                >
                  <input
                    className="input"
                    placeholder="Add decision…"
                    value={decisionInput}
                    disabled={busy}
                    onChange={(e) => setDecisionInput(e.target.value)}
                  />
                  <button type="submit" disabled={busy}>
                    Add
                  </button>
                </form>
              ) : null}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Comments</h3>
              <span className="pill">{comments.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {onAddComment ? (
                <div className="commentComposer">
                  <textarea
                    className="input"
                    placeholder="Write a comment…"
                    value={commentInput}
                    disabled={busy}
                    onChange={(e) => setCommentInput(e.target.value)}
                  />
                  <div className="commentComposerActions">
                    <div className="muted small">
                      {commentInput.trim().length}/2000
                    </div>
                    <button
                      type="button"
                      disabled={!commentInput.trim() || busy}
                      onClick={() =>
                        safeRun(async () => {
                          await onAddComment(taskId, commentInput.trim());
                          setCommentInput("");
                        })
                      }
                    >
                      Post
                    </button>
                  </div>
                </div>
              ) : null}

              {comments.length ? (
                <div className="commentList">
                  {comments
                    .slice()
                    .sort(
                      (a, b) =>
                        new Date(a.createdAt).getTime() -
                        new Date(b.createdAt).getTime(),
                    )
                    .map((c) => (
                      <div key={c.id} className="commentItem">
                        <div className="commentHeader">
                          <div className="commentHeaderLeft">
                            <strong className="commentAuthor">
                              {c.authorEmail || "User"}
                            </strong>
                            <span className="muted small">Comment</span>
                          </div>
                          <span className="muted small">
                            {formatDateTime(c.createdAt)}
                          </span>
                        </div>
                        <div className="commentMessage">{c.message}</div>
                      </div>
                    ))}
                </div>
              ) : (
                <div className="muted">No comments yet</div>
              )}
            </div>
          </section>

          <section className="taskModalSection">
            <div className="taskModalSectionHeader">
              <h3>Activity</h3>
              <span className="pill">{activity.length}</span>
            </div>
            <div className="taskModalSectionBody">
              {activity.length ? (
                <div className="activityList">
                  {activity.map((a) => (
                    <div key={a.id} className="activityItem">
                      <div className="activityHeader">
                        <strong>{activityLabel(a)}</strong>
                        <span className="muted small">
                          {formatDateTime(a.createdAt)}
                        </span>
                      </div>
                      <div className="activityMessage">
                        {a.message ? a.message : null}
                        {a.fromStatus || a.toStatus ? (
                          <div className="muted small">
                            {a.fromStatus ? (
                              <span>
                                From: <strong>{a.fromStatus}</strong>
                              </span>
                            ) : null}
                            {a.toStatus ? (
                              <span>
                                {a.fromStatus ? " • " : ""}To:{" "}
                                <strong>{a.toStatus}</strong>
                              </span>
                            ) : null}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="muted">No activity yet</div>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
