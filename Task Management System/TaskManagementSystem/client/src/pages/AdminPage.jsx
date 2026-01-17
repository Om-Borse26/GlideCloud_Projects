import { useEffect, useMemo, useState } from "react";
import {
  DndContext,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { SortableContext, arrayMove, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { api, getApiErrorMessage } from "../lib/apiClient";
import { clearToken } from "../lib/auth";
import { useTaskDetailsMutations } from "../lib/tasksClient";
import { ThemeToggle } from "../components/ThemeToggle";
import LoadingSpinner from "../components/LoadingSpinner";
import TaskDetailsModal from "../ui/TaskDetailsModal";

const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];

function splitEmails(text) {
  return text
    .split(/[\n,]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

const DEFAULT_PANELS = ["createGroup", "groups", "assignUser", "assignGroup"];

function SortableAdminPanel({ id, enabled, children }) {
  const sortableId = `ADMIN_${id}`;
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: sortableId,
    disabled: !enabled,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.75 : 1,
  };

  const headerDragHandleProps = enabled
    ? { ...attributes, ...listeners }
    : null;
  return (
    <div ref={setNodeRef} style={style} className="adminPanelWrap">
      {children({ headerDragHandleProps })}
    </div>
  );
}

export default function AdminPage() {
  const [tasks, setTasks] = useState([]);
  const [groups, setGroups] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [groupName, setGroupName] = useState("");
  const [groupMembersText, setGroupMembersText] = useState("");

  const [editingGroupId, setEditingGroupId] = useState("");
  const [editingMembersText, setEditingMembersText] = useState("");

  const [assignToUser, setAssignToUser] = useState({
    assigneeEmail: "",
    title: "",
    description: "",
    priority: "MEDIUM",
    dueDate: "",
  });

  const [assignToGroup, setAssignToGroup] = useState({
    groupId: "",
    title: "",
    description: "",
    priority: "MEDIUM",
    dueDate: "",
  });

  const [layoutMode, setLayoutMode] = useState(
    () => localStorage.getItem("admin:layoutMode") === "1"
  );
  const [panelOrder, setPanelOrder] = useState(() => {
    try {
      const raw = localStorage.getItem("admin:panelOrder");
      const parsed = raw ? JSON.parse(raw) : null;
      const ok =
        Array.isArray(parsed) && parsed.every((x) => typeof x === "string");
      return ok ? parsed : DEFAULT_PANELS;
    } catch {
      return DEFAULT_PANELS;
    }
  });

  useEffect(() => {
    localStorage.setItem("admin:layoutMode", layoutMode ? "1" : "0");
  }, [layoutMode]);

  useEffect(() => {
    localStorage.setItem("admin:panelOrder", JSON.stringify(panelOrder));
  }, [panelOrder]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } })
  );

  const orderedPanels = useMemo(() => {
    const order = (
      panelOrder && panelOrder.length ? panelOrder : DEFAULT_PANELS
    ).slice();
    for (const p of DEFAULT_PANELS) {
      if (!order.includes(p)) order.push(p);
    }
    return order;
  }, [panelOrder]);

  const [taskQuery, setTaskQuery] = useState("");
  const [ownerFilter, setOwnerFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");

  const [detailsTask, setDetailsTask] = useState(null);

  const groupsById = useMemo(() => {
    const map = new Map();
    for (const g of groups) map.set(g.id, g);
    return map;
  }, [groups]);

  const owners = useMemo(() => {
    const set = new Set();
    for (const t of tasks || []) {
      if (t.ownerEmail) set.add(String(t.ownerEmail));
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [tasks]);

  const filteredTasks = useMemo(() => {
    const q = taskQuery.trim().toLowerCase();
    const list = [...(tasks || [])];
    list.sort((a, b) => {
      const ap = a.pinned ? 1 : 0;
      const bp = b.pinned ? 1 : 0;
      if (bp !== ap) return bp - ap;
      const aa = a.assigned ? 1 : 0;
      const ba = b.assigned ? 1 : 0;
      if (ba !== aa) return ba - aa;
      return String(a.title || "").localeCompare(String(b.title || ""));
    });
    return list.filter((t) => {
      if (ownerFilter && String(t.ownerEmail || "") !== ownerFilter)
        return false;
      if (statusFilter && String(t.status || "") !== statusFilter) return false;
      if (priorityFilter && String(t.priority || "") !== priorityFilter)
        return false;

      if (!q) return true;
      const hay = `${t.title || ""} ${t.ownerEmail || ""} ${
        t.createdByEmail || ""
      } ${t.status || ""} ${t.priority || ""}`.toLowerCase();
      return hay.includes(q);
    });
  }, [tasks, taskQuery, ownerFilter, statusFilter, priorityFilter]);

  const statusCounts = useMemo(() => {
    const counts = { TODO: 0, IN_PROGRESS: 0, DONE: 0 };
    for (const t of filteredTasks) {
      if (counts[t.status] != null) counts[t.status]++;
    }
    return counts;
  }, [filteredTasks]);

  function applyTemplateFromTask(task) {
    const base = {
      title: task?.title || "",
      description: task?.description || "",
      priority: task?.priority || "MEDIUM",
      dueDate: task?.dueDate || "",
    };

    setAssignToUser((p) => ({ ...p, ...base }));
    setAssignToGroup((p) => ({ ...p, ...base }));

    // Best-effort: scroll near the assign section for visibility.
    requestAnimationFrame(() => {
      const el = document.getElementById("assign-sections");
      if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }

  function csvEscape(value) {
    const s = value == null ? "" : String(value);
    // Escape quotes by doubling, and wrap if needed.
    if (/[",\n]/.test(s)) return `"${s.replaceAll('"', '""')}"`;
    return s;
  }

  function exportCsv() {
    const header = [
      "id",
      "title",
      "status",
      "priority",
      "ownerEmail",
      "createdByEmail",
      "assigned",
      "pinned",
      "dueDate",
      "createdAt",
      "updatedAt",
    ];

    const rows = filteredTasks.map((t) => [
      t.id,
      t.title,
      t.status,
      t.priority,
      t.ownerEmail,
      t.createdByEmail,
      t.assigned,
      t.pinned,
      t.dueDate,
      t.createdAt,
      t.updatedAt,
    ]);

    const csv = [
      header.join(","),
      ...rows.map((r) => r.map(csvEscape).join(",")),
    ].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    const stamp = new Date().toISOString().slice(0, 19).replaceAll(":", "-");
    a.download = `tms-admin-tasks-${stamp}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  async function reload() {
    setError("");
    setSuccess("");
    setLoading(true);
    try {
      const [tasksRes, groupsRes] = await Promise.all([
        api.get("/api/admin/tasks"),
        api.get("/api/admin/groups"),
      ]);
      setTasks(tasksRes.data || []);
      setGroups(groupsRes.data || []);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reload();
  }, []);

  async function createGroup() {
    setError("");
    setSuccess("");
    const name = groupName.trim();
    if (!name) return;

    try {
      await api.post("/api/admin/groups", {
        name,
        memberEmails: splitEmails(groupMembersText),
      });
      setGroupName("");
      setGroupMembersText("");
      setSuccess("Group created");
      await reload();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

  async function startEditGroup(group) {
    setEditingGroupId(group.id);
    setEditingMembersText((group.memberEmails || []).join("\n"));
  }

  async function saveGroupMembers() {
    if (!editingGroupId) return;
    setError("");
    setSuccess("");
    try {
      await api.put(`/api/admin/groups/${editingGroupId}`, {
        memberEmails: splitEmails(editingMembersText),
      });
      setEditingGroupId("");
      setEditingMembersText("");
      setSuccess("Group updated");
      await reload();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

  function normalizeDueDate(value) {
    const v = String(value || "").trim();
    return v ? v : null;
  }

  async function submitAssignToUser() {
    setError("");
    setSuccess("");

    try {
      await api.post("/api/admin/tasks/assign/user", {
        assigneeEmail: assignToUser.assigneeEmail,
        title: assignToUser.title,
        description: assignToUser.description,
        priority: assignToUser.priority,
        dueDate: normalizeDueDate(assignToUser.dueDate),
      });
      setAssignToUser({
        assigneeEmail: "",
        title: "",
        description: "",
        priority: "MEDIUM",
        dueDate: "",
      });
      setSuccess("Task assigned to user");
      await reload();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

  async function submitAssignToGroup() {
    setError("");
    setSuccess("");

    try {
      await api.post("/api/admin/tasks/assign/group", {
        groupId: assignToGroup.groupId,
        title: assignToGroup.title,
        description: assignToGroup.description,
        priority: assignToGroup.priority,
        dueDate: normalizeDueDate(assignToGroup.dueDate),
      });
      setAssignToGroup({
        groupId: "",
        title: "",
        description: "",
        priority: "MEDIUM",
        dueDate: "",
      });
      setSuccess("Task assigned to group");
      await reload();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

  async function openTaskDetails(taskId) {
    setError("");
    setSuccess("");
    try {
      const res = await api.get(`/api/admin/tasks/${taskId}`);
      setDetailsTask(res.data);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  }

  const { addComment } = useTaskDetailsMutations({
    setTask: setDetailsTask,
    setError,
  });

  return (
    <div className="adminPage">
      <ThemeToggle />
      <header className="topbar">
        <div>
          <h1>Admin Dashboard</h1>
          <div className="muted">Read-only global tasks + assignment</div>
        </div>
        <div className="topbarActions">
          <button
            type="button"
            className="secondary"
            onClick={() => setLayoutMode((v) => !v)}
            title="Rearrange and resize admin panels"
          >
            {layoutMode ? "Done" : "Customize"}
          </button>
          <a className="secondary" href="/board">
            Board
          </a>
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

      {loading ? <LoadingSpinner message="Loading..." /> : null}
      {error ? <div className="error">{error}</div> : null}
      {success ? <div className="success">{success}</div> : null}

      <DndContext
        sensors={sensors}
        onDragEnd={({ active, over }) => {
          if (!layoutMode) return;
          if (!over) return;
          const a = String(active.id);
          const o = String(over.id);
          if (!a.startsWith("ADMIN_") || !o.startsWith("ADMIN_")) return;
          const fromKey = a.replace("ADMIN_", "");
          const toKey = o.replace("ADMIN_", "");
          const fromIndex = orderedPanels.indexOf(fromKey);
          const toIndex = orderedPanels.indexOf(toKey);
          if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return;
          setPanelOrder(arrayMove(orderedPanels, fromIndex, toIndex));
        }}
      >
        <SortableContext items={orderedPanels.map((p) => `ADMIN_${p}`)}>
          <div className={`adminGrid ${layoutMode ? "layoutMode" : ""}`}>
            {orderedPanels.map((panelId) => (
              <SortableAdminPanel
                key={panelId}
                id={panelId}
                enabled={layoutMode}
              >
                {({ headerDragHandleProps }) => {
                  if (panelId === "createGroup") {
                    return (
                      <section className="adminCard">
                        <h2 {...(headerDragHandleProps || {})}>Create Group</h2>
                        <div className="form">
                          <label>
                            Group name
                            <input
                              value={groupName}
                              onChange={(e) => setGroupName(e.target.value)}
                              placeholder="e.g. QA"
                            />
                          </label>
                          <label>
                            Member emails (comma or newline separated)
                            <textarea
                              value={groupMembersText}
                              onChange={(e) =>
                                setGroupMembersText(e.target.value)
                              }
                              placeholder="alice@example.com\nbob@example.com"
                              rows={4}
                            />
                          </label>
                          <button type="button" onClick={createGroup}>
                            Create group
                          </button>
                        </div>
                      </section>
                    );
                  }

                  if (panelId === "groups") {
                    return (
                      <section className="adminCard">
                        <h2 {...(headerDragHandleProps || {})}>Groups</h2>
                        {groups.length === 0 ? (
                          <div className="muted">No groups yet</div>
                        ) : null}
                        <div className="groupList">
                          {groups.map((g) => (
                            <div key={g.id} className="groupRow">
                              <div className="groupMeta">
                                <strong>{g.name}</strong>
                                <div className="muted small">
                                  {(g.memberEmails || []).join(", ") || "—"}
                                </div>
                              </div>
                              <button
                                className="secondary"
                                type="button"
                                onClick={() => startEditGroup(g)}
                              >
                                Edit members
                              </button>
                            </div>
                          ))}
                        </div>

                        {editingGroupId ? (
                          <div className="editBox">
                            <div className="muted small">
                              Editing: {groupsById.get(editingGroupId)?.name}
                            </div>
                            <textarea
                              value={editingMembersText}
                              onChange={(e) =>
                                setEditingMembersText(e.target.value)
                              }
                              rows={5}
                            />
                            <div className="row">
                              <button
                                className="secondary"
                                type="button"
                                onClick={() => {
                                  setEditingGroupId("");
                                  setEditingMembersText("");
                                }}
                              >
                                Cancel
                              </button>
                              <button type="button" onClick={saveGroupMembers}>
                                Save
                              </button>
                            </div>
                          </div>
                        ) : null}
                      </section>
                    );
                  }

                  if (panelId === "assignUser") {
                    return (
                      <section className="adminCard" id="assign-sections">
                        <h2 {...(headerDragHandleProps || {})}>
                          Assign Task to User
                        </h2>
                        <div className="form">
                          <label>
                            Assignee email
                            <input
                              value={assignToUser.assigneeEmail}
                              onChange={(e) =>
                                setAssignToUser((p) => ({
                                  ...p,
                                  assigneeEmail: e.target.value,
                                }))
                              }
                            />
                          </label>
                          <label>
                            Title
                            <input
                              value={assignToUser.title}
                              onChange={(e) =>
                                setAssignToUser((p) => ({
                                  ...p,
                                  title: e.target.value,
                                }))
                              }
                            />
                          </label>
                          <label>
                            Description
                            <textarea
                              value={assignToUser.description}
                              onChange={(e) =>
                                setAssignToUser((p) => ({
                                  ...p,
                                  description: e.target.value,
                                }))
                              }
                              rows={3}
                            />
                          </label>
                          <div className="row">
                            <label>
                              Priority
                              <select
                                className="select"
                                value={assignToUser.priority}
                                onChange={(e) =>
                                  setAssignToUser((p) => ({
                                    ...p,
                                    priority: e.target.value,
                                  }))
                                }
                              >
                                {PRIORITIES.map((p) => (
                                  <option key={p} value={p}>
                                    {p}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label>
                              Due date
                              <input
                                type="date"
                                value={assignToUser.dueDate}
                                onChange={(e) =>
                                  setAssignToUser((p) => ({
                                    ...p,
                                    dueDate: e.target.value,
                                  }))
                                }
                              />
                            </label>
                          </div>
                          <button type="button" onClick={submitAssignToUser}>
                            Assign
                          </button>
                        </div>
                      </section>
                    );
                  }

                  // assignGroup
                  return (
                    <section className="adminCard">
                      <h2 {...(headerDragHandleProps || {})}>
                        Assign Task to Group
                      </h2>
                      <div className="form">
                        <label>
                          Group
                          <select
                            className="select"
                            value={assignToGroup.groupId}
                            onChange={(e) =>
                              setAssignToGroup((p) => ({
                                ...p,
                                groupId: e.target.value,
                              }))
                            }
                          >
                            <option value="">Select a group…</option>
                            {groups.map((g) => (
                              <option key={g.id} value={g.id}>
                                {g.name}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label>
                          Title
                          <input
                            value={assignToGroup.title}
                            onChange={(e) =>
                              setAssignToGroup((p) => ({
                                ...p,
                                title: e.target.value,
                              }))
                            }
                          />
                        </label>
                        <label>
                          Description
                          <textarea
                            value={assignToGroup.description}
                            onChange={(e) =>
                              setAssignToGroup((p) => ({
                                ...p,
                                description: e.target.value,
                              }))
                            }
                            rows={3}
                          />
                        </label>
                        <div className="row">
                          <label>
                            Priority
                            <select
                              className="select"
                              value={assignToGroup.priority}
                              onChange={(e) =>
                                setAssignToGroup((p) => ({
                                  ...p,
                                  priority: e.target.value,
                                }))
                              }
                            >
                              {PRIORITIES.map((p) => (
                                <option key={p} value={p}>
                                  {p}
                                </option>
                              ))}
                            </select>
                          </label>
                          <label>
                            Due date
                            <input
                              type="date"
                              value={assignToGroup.dueDate}
                              onChange={(e) =>
                                setAssignToGroup((p) => ({
                                  ...p,
                                  dueDate: e.target.value,
                                }))
                              }
                            />
                          </label>
                        </div>
                        <button
                          type="button"
                          disabled={!assignToGroup.groupId}
                          onClick={submitAssignToGroup}
                        >
                          Assign
                        </button>
                      </div>
                    </section>
                  );
                }}
              </SortableAdminPanel>
            ))}
          </div>
        </SortableContext>
      </DndContext>

      <section className="adminCard fullWidth">
        <div className="row spaceBetween">
          <h2>All Tasks (Read-only)</h2>
          <div className="row">
            <input
              value={taskQuery}
              onChange={(e) => setTaskQuery(e.target.value)}
              placeholder="Search title, owner, status…"
              style={{ maxWidth: 320 }}
            />
            <select
              className="select"
              value={ownerFilter}
              onChange={(e) => setOwnerFilter(e.target.value)}
            >
              <option value="">All owners</option>
              {owners.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
            <select
              className="select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="">All statuses</option>
              <option value="TODO">TODO</option>
              <option value="IN_PROGRESS">IN_PROGRESS</option>
              <option value="DONE">DONE</option>
            </select>
            <select
              className="select"
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
            >
              <option value="">All priorities</option>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setTaskQuery("");
                setOwnerFilter("");
                setStatusFilter("");
                setPriorityFilter("");
              }}
              title="Clear filters"
            >
              Clear
            </button>
            <button
              type="button"
              className="secondary"
              onClick={exportCsv}
              disabled={!filteredTasks.length}
              title="Export currently visible tasks"
            >
              Export CSV
            </button>
            <button type="button" className="secondary" onClick={reload}>
              Refresh
            </button>
          </div>
        </div>

        <div className="adminSummary">
          <span className="badge">TODO: {statusCounts.TODO}</span>
          <span className="badge">IN_PROGRESS: {statusCounts.IN_PROGRESS}</span>
          <span className="badge">DONE: {statusCounts.DONE}</span>
          <span className="muted small">
            Showing {filteredTasks.length} tasks
          </span>
        </div>

        {filteredTasks.length === 0 && !loading ? (
          <div className="muted">No tasks found</div>
        ) : null}

        {filteredTasks.length ? (
          <div className="tableWrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Owner</th>
                  <th>Created By</th>
                  <th>Flags</th>
                  <th>Due</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredTasks.map((t) => (
                  <tr key={t.id}>
                    <td>{t.title}</td>
                    <td>
                      <span
                        className={`chip status ${String(
                          t.status || ""
                        ).toLowerCase()}`}
                      >
                        {t.status}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`chip prio ${String(
                          t.priority || ""
                        ).toLowerCase()}`}
                      >
                        {t.priority}
                      </span>
                    </td>
                    <td>{t.ownerEmail}</td>
                    <td>{t.createdByEmail}</td>
                    <td>
                      {t.assigned ? (
                        <span className="badge assigned">ASSIGNED</span>
                      ) : null}
                      {t.pinned ? (
                        <span className="badge pinned">PINNED</span>
                      ) : null}
                    </td>
                    <td>{t.dueDate || "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="secondary mini"
                        onClick={() => applyTemplateFromTask(t)}
                        title="Prefill assignment forms using this task"
                      >
                        Use as template
                      </button>

                      <button
                        type="button"
                        className="secondary mini"
                        onClick={() => openTaskDetails(t.id)}
                        title="View task comments and activity"
                        style={{ marginLeft: 8 }}
                      >
                        Details
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      <TaskDetailsModal
        open={Boolean(detailsTask)}
        task={detailsTask}
        onClose={() => setDetailsTask(null)}
        onAddComment={addComment}
      />
    </div>
  );
}
