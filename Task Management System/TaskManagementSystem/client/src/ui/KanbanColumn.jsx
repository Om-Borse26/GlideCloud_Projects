import { useDroppable } from "@dnd-kit/core";
import TaskCard from "./TaskCard";

export default function Column({
  id,
  title,
  tasks,
  wipLimit,
  onDeleteTask,
  onMoveToStatus,
  onUpdatePriority,
  onOpenDetails,
  selectedTaskIds,
  onToggleTaskSelected,
  headerDragHandleProps,
  layoutMode = false,
}) {
  const { setNodeRef, isOver } = useDroppable({ id, disabled: layoutMode });
  const limit =
    typeof wipLimit === "number" && Number.isFinite(wipLimit) && wipLimit > 0
      ? Math.floor(wipLimit)
      : null;
  const overLimit = limit != null && tasks.length > limit;

  return (
    <section className={`column ${isOver ? "over" : ""}`} ref={setNodeRef}>
      <header className="columnHeader" {...(headerDragHandleProps || {})}>
        <h2>{title}</h2>
        <span
          className={`pill ${overLimit ? "danger" : ""}`}
          title={limit != null ? `WIP limit: ${limit}` : undefined}
        >
          {limit != null ? `${tasks.length}/${limit}` : tasks.length}
        </span>
      </header>
      <div className="columnBody">
        {tasks.map((t) => (
          <TaskCard
            key={t.id}
            task={t}
            onDelete={() => onDeleteTask(t.id)}
            onMoveToStatus={(toStatus) => onMoveToStatus(t.id, toStatus, 0)}
            onUpdatePriority={(priority) => onUpdatePriority(t, priority)}
            onOpenDetails={onOpenDetails}
            selected={Boolean(selectedTaskIds?.has?.(t.id))}
            onToggleSelected={onToggleTaskSelected}
            dndDisabled={layoutMode}
          />
        ))}
      </div>
    </section>
  );
}
