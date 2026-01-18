import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useState } from "react";
import { useFocusTimer } from "../context/FocusTimerContext";
import { formatHhMmSs } from "../lib/timeFormat";

export default function TaskCard({
  task,
  onDelete,
  onMoveToStatus,
  onUpdatePriority,
  onOpenDetails,
  selected = false,
  onToggleSelected,
  dndDisabled = false,
}) {
  const isAssigned = Boolean(task.assigned);
  const isPinned = Boolean(task.pinned);
  const dragDisabled = Boolean(dndDisabled || isAssigned);

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: task.id,
    disabled: dragDisabled,
  });

  const [confirmDelete, setConfirmDelete] = useState(false);

  const { activeTaskId, running, elapsedSeconds, start, stop, resume, clear } =
    useFocusTimer();
  const isActiveTimer = activeTaskId === task.id;

  const labels = Array.isArray(task.labels) ? task.labels : [];
  const checklistDone = Number(task.checklistDone || 0);
  const checklistTotal = Number(task.checklistTotal || 0);
  const checklistPct =
    checklistTotal > 0
      ? Math.round((Math.max(0, checklistDone) / checklistTotal) * 100)
      : 0;

  const dueDate = task.dueDate ? String(task.dueDate) : "";
  const isOverdue = (() => {
    if (!dueDate) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const due = new Date(dueDate + "T00:00:00");
    return due.getTime() < today.getTime();
  })();

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  };

  return (
    <article
      ref={setNodeRef}
      style={style}
      className={`taskCard ${isAssigned ? "assignedTask" : ""} ${
        isPinned ? "pinnedTask" : ""
      }`}
    >
      <div
        className={`taskTop ${dragDisabled ? "noDrag" : ""}`}
        {...(!dragDisabled ? attributes : {})}
        {...(!dragDisabled ? listeners : {})}
      >
        <strong className="taskTitle">{task.title}</strong>
        <div className="taskBadges">
          {isAssigned ? <span className="tag assigned">Assigned</span> : null}
          {isPinned ? <span className="tag pinned">Pinned</span> : null}
          <span
            className={`priority ${String(
              task.priority || "MEDIUM",
            ).toLowerCase()}`}
          >
            {task.priority}
          </span>
        </div>
      </div>
      {task.description ? <p className="taskDesc">{task.description}</p> : null}

      {labels.length ? (
        <div className="taskLabels">
          {labels.slice(0, 4).map((l) => (
            <span key={l} className="labelChip small">
              {l}
            </span>
          ))}
          {labels.length > 4 ? (
            <span className="muted small">+{labels.length - 4}</span>
          ) : null}
        </div>
      ) : null}

      {dueDate ? (
        <div className={`taskMeta ${isOverdue ? "overdue" : ""}`}>
          Due: <strong>{dueDate}</strong>
        </div>
      ) : null}

      {checklistTotal ? (
        <div className="taskMeta">
          ☑ <strong>{checklistDone}</strong>/{checklistTotal} ({checklistPct}%)
        </div>
      ) : null}
      <div className="taskActions">
        <div className="taskLeftActions">
          {onToggleSelected ? (
            <label
              className="selectTask"
              title={selected ? "Unselect" : "Select"}
              onPointerDown={(e) => e.stopPropagation()}
              onMouseDown={(e) => e.stopPropagation()}
              onClick={(e) => e.stopPropagation()}
            >
              <input
                type="checkbox"
                checked={Boolean(selected)}
                onChange={() => onToggleSelected?.(task.id)}
                disabled={dndDisabled}
              />
            </label>
          ) : null}
          <button
            type="button"
            className="mini"
            title="Open details"
            onClick={(e) => {
              e.stopPropagation();
              onOpenDetails?.(task);
            }}
          >
            ⋯
          </button>
          <button
            type="button"
            className="mini"
            title="Move to To Do"
            onClick={() => onMoveToStatus("TODO")}
          >
            ☐
          </button>
          <button
            type="button"
            className="mini"
            title="Move to In Progress"
            onClick={() => onMoveToStatus("IN_PROGRESS")}
          >
            ▶
          </button>
          <button
            type="button"
            className="mini"
            title="Move to Done"
            onClick={() => onMoveToStatus("DONE")}
          >
            ✓
          </button>

          <select
            className="select miniSelect"
            value={task.priority || "MEDIUM"}
            onChange={(e) => onUpdatePriority(e.target.value)}
            title="Priority"
            disabled={isAssigned}
          >
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
          </select>

          <button
            type="button"
            className={`mini timerBtn ${isActiveTimer ? "active" : ""}`}
            title={
              isActiveTimer
                ? running
                  ? "Pause timer"
                  : "Resume timer"
                : "Start focus timer"
            }
            onClick={() => {
              if (!isActiveTimer) return start(task.id);
              return running ? stop() : resume();
            }}
          >
            {isActiveTimer ? (running ? "⏸" : "▶") : "⏱"}
          </button>

          {isActiveTimer ? (
            <span className="timerReadout" title="Focus timer">
              {formatHhMmSs(elapsedSeconds)}
              <button
                type="button"
                className="timerClear"
                title="Clear timer"
                onClick={clear}
              >
                ✕
              </button>
            </span>
          ) : null}
        </div>

        {isAssigned ? (
          <div className="muted small">Assigned tasks can’t be deleted.</div>
        ) : !confirmDelete ? (
          <button
            className="danger"
            type="button"
            onClick={() => setConfirmDelete(true)}
          >
            Delete
          </button>
        ) : (
          <div className="confirmRow">
            <button
              className="secondary"
              type="button"
              onClick={() => setConfirmDelete(false)}
            >
              Cancel
            </button>
            <button
              className="danger"
              type="button"
              onClick={() => {
                setConfirmDelete(false);
                onDelete();
              }}
            >
              Confirm
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
