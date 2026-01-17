import {
  DndContext,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  horizontalListSortingStrategy,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { useMemo, useState } from "react";
import Column from "./KanbanColumn";
import { CSS } from "@dnd-kit/utilities";

const STATUSES = [
  { key: "TODO", title: "To Do" },
  { key: "IN_PROGRESS", title: "In Progress" },
  { key: "DONE", title: "Done" },
];

function SortableColumn({
  column,
  tasks,
  wipLimit,
  onDeleteTask,
  onMoveToStatus,
  onUpdatePriority,
  onOpenDetails,
  selectedTaskIds,
  onToggleTaskSelected,
  layoutMode,
}) {
  const colId = `COL_${column.key}`;
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: colId,
    disabled: !layoutMode,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.75 : 1,
    zIndex: isDragging ? 50 : "auto",
  };

  const headerDragHandleProps = layoutMode
    ? { ...attributes, ...listeners }
    : null;

  return (
    <div ref={setNodeRef} style={style} className="columnWrap">
      <Column
        id={column.key}
        title={column.title}
        tasks={tasks}
        wipLimit={wipLimit}
        onDeleteTask={onDeleteTask}
        onMoveToStatus={onMoveToStatus}
        onUpdatePriority={onUpdatePriority}
        onOpenDetails={onOpenDetails}
        selectedTaskIds={selectedTaskIds}
        onToggleTaskSelected={onToggleTaskSelected}
        headerDragHandleProps={headerDragHandleProps}
        layoutMode={layoutMode}
      />
    </div>
  );
}

export default function KanbanBoard({
  tasksByStatus,
  onCreateTask,
  onDeleteTask,
  onMoveTask,
  onUpdatePriority,
  onOpenDetails,
  selectedTaskIds,
  onToggleTaskSelected,
  layoutMode = false,
  wipLimits,
  columnOrder = ["TODO", "IN_PROGRESS", "DONE"],
  onColumnOrderChange,
}) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 3 } })
  );
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState("MEDIUM");
  const [newDueDate, setNewDueDate] = useState("");

  const orderedStatuses = useMemo(() => {
    const byKey = new Map(STATUSES.map((s) => [s.key, s]));
    const ordered = [];
    for (const key of columnOrder || []) {
      const s = byKey.get(key);
      if (s) ordered.push(s);
    }
    for (const s of STATUSES) {
      if (!ordered.some((x) => x.key === s.key)) ordered.push(s);
    }
    return ordered;
  }, [columnOrder]);

  const taskIdToStatus = useMemo(() => {
    const map = new Map();
    for (const s of STATUSES) {
      for (const t of tasksByStatus[s.key] || []) {
        map.set(t.id, s.key);
      }
    }
    return map;
  }, [tasksByStatus]);

  const taskIdToPinned = useMemo(() => {
    const map = new Map();
    for (const s of STATUSES) {
      for (const t of tasksByStatus[s.key] || []) {
        map.set(t.id, Boolean(t.pinned));
      }
    }
    return map;
  }, [tasksByStatus]);

  function splitSegments(list) {
    const pinned = [];
    const unpinned = [];
    for (const t of list) {
      (t.pinned ? pinned : unpinned).push(t);
    }
    return { pinned, unpinned };
  }

  function segmentIndexForDrop(destList, isPinnedMove, dropIndexInFullList) {
    // Backend treats `toIndex` as index within pinned/unpinned segment.
    // Convert a drop index in the full list into the segment-local index.
    const { pinned, unpinned } = splitSegments(destList);
    const pinnedCount = pinned.length;

    if (isPinnedMove) {
      return Math.max(0, Math.min(dropIndexInFullList, pinnedCount));
    }

    // Dropping into unpinned segment: if drop index is within pinned part,
    // treat it as insertion at the start of unpinned.
    const raw = dropIndexInFullList - pinnedCount;
    return Math.max(0, Math.min(raw, unpinned.length));
  }

  function buildNextTasks(nextByStatus) {
    const all = [];
    for (const s of STATUSES) {
      const arr = nextByStatus[s.key] || [];
      for (let i = 0; i < arr.length; i++) {
        all.push({ ...arr[i], status: s.key, position: i });
      }
    }
    return all;
  }

  function onDragEnd(event) {
    const { active, over } = event;
    if (!over) return;

    const activeId = String(active.id);
    const overId = String(over.id);

    // Column reordering (layout mode)
    if (
      layoutMode &&
      activeId.startsWith("COL_") &&
      overId.startsWith("COL_")
    ) {
      const fromKey = activeId.replace("COL_", "");
      const toKey = overId.replace("COL_", "");
      const current = (
        columnOrder && columnOrder.length
          ? columnOrder
          : STATUSES.map((s) => s.key)
      ).slice();
      const fromIndex = current.indexOf(fromKey);
      const toIndex = current.indexOf(toKey);
      if (fromIndex >= 0 && toIndex >= 0 && fromIndex !== toIndex) {
        const next = arrayMove(current, fromIndex, toIndex);
        onColumnOrderChange?.(next);
      }
      return;
    }

    const fromStatus = taskIdToStatus.get(activeId);
    if (!fromStatus) return;

    const isPinnedMove = Boolean(taskIdToPinned.get(activeId));

    const overStatus =
      taskIdToStatus.get(overId) ||
      (STATUSES.some((s) => s.key === overId) ? overId : null);
    const toStatus = overStatus || fromStatus;

    const fromList = [...(tasksByStatus[fromStatus] || [])];
    const toList =
      fromStatus === toStatus ? fromList : [...(tasksByStatus[toStatus] || [])];

    const fromIndex = fromList.findIndex((t) => t.id === activeId);
    if (fromIndex < 0) return;

    // If dropping on a task, use that task's index; if dropping on column, append.
    const overTaskIndex = toList.findIndex((t) => t.id === overId);
    const toIndex = overTaskIndex >= 0 ? overTaskIndex : toList.length;

    const moved = fromList[fromIndex];

    let nextByStatus = {
      TODO: [...(tasksByStatus.TODO || [])],
      IN_PROGRESS: [...(tasksByStatus.IN_PROGRESS || [])],
      DONE: [...(tasksByStatus.DONE || [])],
    };

    if (fromStatus === toStatus) {
      const { pinned, unpinned } = splitSegments(fromList);
      const sourceSegment = isPinnedMove ? pinned : unpinned;
      const fromIdx = sourceSegment.findIndex((t) => t.id === activeId);
      if (fromIdx < 0) return;

      const segToIndex = segmentIndexForDrop(fromList, isPinnedMove, toIndex);
      const movedSeg = arrayMove(
        sourceSegment,
        fromIdx,
        Math.max(0, Math.min(segToIndex, sourceSegment.length - 1))
      );
      const merged = isPinnedMove
        ? [...movedSeg, ...unpinned]
        : [...pinned, ...movedSeg];
      nextByStatus[fromStatus] = merged;
    } else {
      // remove from source (within its segment)
      nextByStatus[fromStatus] = fromList.filter((t) => t.id !== activeId);

      // insert into destination within the matching segment
      const destList = [...toList];
      const { pinned, unpinned } = splitSegments(destList);
      const destSegment = isPinnedMove ? [...pinned] : [...unpinned];
      const segToIndex = segmentIndexForDrop(destList, isPinnedMove, toIndex);
      destSegment.splice(segToIndex, 0, {
        ...moved,
        status: toStatus,
        pinned: isPinnedMove,
      });

      nextByStatus[toStatus] = isPinnedMove
        ? [...destSegment, ...unpinned]
        : [...pinned, ...destSegment];
    }

    onMoveTask({
      taskId: activeId,
      fromStatus,
      toStatus,
      toIndex: segmentIndexForDrop(toList, isPinnedMove, toIndex),
      nextTasks: buildNextTasks(nextByStatus),
    });
  }

  function moveByButton(taskId, toStatus, toIndex = 0) {
    const fromStatus = taskIdToStatus.get(taskId);
    if (!fromStatus) return;

    const isPinnedMove = Boolean(taskIdToPinned.get(taskId));

    const fromList = [...(tasksByStatus[fromStatus] || [])];
    const toList =
      fromStatus === toStatus ? fromList : [...(tasksByStatus[toStatus] || [])];

    const fromIndex = fromList.findIndex((t) => t.id === taskId);
    if (fromIndex < 0) return;

    const moved = fromList[fromIndex];

    let nextByStatus = {
      TODO: [...(tasksByStatus.TODO || [])],
      IN_PROGRESS: [...(tasksByStatus.IN_PROGRESS || [])],
      DONE: [...(tasksByStatus.DONE || [])],
    };

    if (fromStatus === toStatus) {
      const { pinned, unpinned } = splitSegments(fromList);
      const segment = isPinnedMove ? pinned : unpinned;
      const fromIdx = segment.findIndex((t) => t.id === taskId);
      if (fromIdx < 0) return;
      const idx = Math.max(0, Math.min(toIndex, segment.length - 1));
      const movedSeg = arrayMove(segment, fromIdx, idx);
      nextByStatus[fromStatus] = isPinnedMove
        ? [...movedSeg, ...unpinned]
        : [...pinned, ...movedSeg];
    } else {
      nextByStatus[fromStatus] = fromList.filter((t) => t.id !== taskId);

      const destList = [...toList];
      const { pinned, unpinned } = splitSegments(destList);
      const destSegment = isPinnedMove ? [...pinned] : [...unpinned];
      const idx = Math.max(0, Math.min(toIndex, destSegment.length));
      destSegment.splice(idx, 0, {
        ...moved,
        status: toStatus,
        pinned: isPinnedMove,
      });
      nextByStatus[toStatus] = isPinnedMove
        ? [...destSegment, ...unpinned]
        : [...pinned, ...destSegment];
    }

    onMoveTask({
      taskId,
      fromStatus,
      toStatus,
      toIndex,
      nextTasks: buildNextTasks(nextByStatus),
    });
  }

  return (
    <div className="board">
      <div className="newTaskRow">
        <input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="Quick add a TODO task…"
        />
        <textarea
          value={newDescription}
          onChange={(e) => setNewDescription(e.target.value)}
          placeholder="Add a description (optional)…"
          rows={2}
        />
        <div className="newTaskMeta">
          <select
            value={newPriority}
            onChange={(e) => setNewPriority(e.target.value)}
            className="select"
            title="Priority"
          >
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
          </select>
          <input
            type="date"
            value={newDueDate}
            onChange={(e) => setNewDueDate(e.target.value)}
            className="select"
            title="Due date"
          />
        </div>
        <button
          onClick={async () => {
            const title = newTitle.trim();
            if (!title) return;
            const description = newDescription.trim();
            const dueDate = newDueDate ? newDueDate : null;
            setNewTitle("");
            setNewDescription("");
            setNewDueDate("");
            await onCreateTask(title, newPriority, description, dueDate);
          }}
        >
          Add
        </button>
      </div>

      <DndContext sensors={sensors} onDragEnd={onDragEnd}>
        <SortableContext
          items={orderedStatuses.map((s) => `COL_${s.key}`)}
          strategy={horizontalListSortingStrategy}
        >
          <div className={`columns ${layoutMode ? "layoutMode" : ""}`}>
            {orderedStatuses.map((s) => (
              <SortableContext
                key={s.key}
                items={(tasksByStatus[s.key] || []).map((t) => t.id)}
                strategy={verticalListSortingStrategy}
              >
                <SortableColumn
                  onOpenDetails={onOpenDetails}
                  column={s}
                  tasks={tasksByStatus[s.key] || []}
                  wipLimit={wipLimits?.[s.key]}
                  onDeleteTask={onDeleteTask}
                  onMoveToStatus={moveByButton}
                  onUpdatePriority={onUpdatePriority}
                  selectedTaskIds={selectedTaskIds}
                  onToggleTaskSelected={onToggleTaskSelected}
                  layoutMode={layoutMode}
                />
              </SortableContext>
            ))}
          </div>
        </SortableContext>
      </DndContext>
    </div>
  );
}
