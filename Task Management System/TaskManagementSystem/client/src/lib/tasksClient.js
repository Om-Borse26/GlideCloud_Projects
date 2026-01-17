import { useCallback, useMemo } from "react";
import { api, getApiErrorMessage } from "./apiClient";

// Loads the full task list for the current user.
export async function fetchTasks() {
  const res = await api.get("/api/tasks");
  return res.data;
}

function replaceTaskInList(list, taskId, nextTask) {
  return list.map((t) => (t?.id === taskId ? nextTask : t));
}

// Centralizes common task mutations (comments, labels, archive, timers, etc.) so pages don't duplicate request+error+state wiring.
export function useTaskMutations({ setTasks, setError, onUnarchivedTaskId }) {
  const run = useCallback(
    async (request) => {
      setError("");
      try {
        const res = await request();
        return res.data;
      } catch (err) {
        setError(getApiErrorMessage(err));
        throw err;
      }
    },
    [setError]
  );

  const applyUpdatedTask = useCallback(
    (taskId, nextTask) => {
      setTasks((prev) => replaceTaskInList(prev, taskId, nextTask));
    },
    [setTasks]
  );

  return useMemo(
    () => ({
      addComment: async (taskId, message) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/comments`, { message })
        );
        applyUpdatedTask(taskId, task);
      },
      updateLabels: async (taskId, labels) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/labels`, { labels })
        );
        applyUpdatedTask(taskId, task);
      },
      updateArchived: async (taskId, archived) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/archive`, { archived })
        );
        applyUpdatedTask(taskId, task);
        if (onUnarchivedTaskId && !archived) {
          onUnarchivedTaskId(taskId);
        }
      },
      addChecklistItem: async (taskId, text) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/checklist`, { text })
        );
        applyUpdatedTask(taskId, task);
      },
      updateChecklistItem: async (taskId, itemId, patch) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/checklist/${itemId}`, {
            text: patch?.text,
            done: patch?.done,
          })
        );
        applyUpdatedTask(taskId, task);
      },
      updateFocus: async (taskId, focus) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/focus`, { focus })
        );
        applyUpdatedTask(taskId, task);
      },
      updateTimeBudget: async (taskId, timeBudgetMinutes) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/time-budget`, { timeBudgetMinutes })
        );
        applyUpdatedTask(taskId, task);
      },
      startTimer: async (taskId) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/timer/start`)
        );
        applyUpdatedTask(taskId, task);
      },
      stopTimer: async (taskId, note) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/timer/stop`, { note: note || null })
        );
        applyUpdatedTask(taskId, task);
      },
      addDecision: async (taskId, message) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/decisions`, { message })
        );
        applyUpdatedTask(taskId, task);
      },
      updateRecurrence: async (taskId, payload) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/recurrence`, payload)
        );
        applyUpdatedTask(taskId, task);
      },
      updateDependencies: async (taskId, blockedByTaskIds) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/dependencies`, { blockedByTaskIds })
        );
        applyUpdatedTask(taskId, task);
      },
    }),
    [applyUpdatedTask, onUnarchivedTaskId, run]
  );
}

// Same task mutation API as useTaskMutations, but targets a single "details" task state (useful for Analytics/Admin pages).
export function useTaskDetailsMutations({ setTask, setError, afterUpdate }) {
  const run = useCallback(
    async (request) => {
      setError("");
      try {
        const res = await request();
        return res.data;
      } catch (err) {
        setError(getApiErrorMessage(err));
        throw err;
      }
    },
    [setError]
  );

  const applyUpdatedTask = useCallback(
    (task) => {
      setTask(task);
    },
    [setTask]
  );

  const after = useCallback(async () => {
    if (typeof afterUpdate === "function") {
      await afterUpdate();
    }
  }, [afterUpdate]);

  return useMemo(
    () => ({
      addComment: async (taskId, message) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/comments`, { message })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateLabels: async (taskId, labels) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/labels`, { labels })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateArchived: async (taskId, archived) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/archive`, { archived })
        );
        applyUpdatedTask(task);
        await after();
      },
      addChecklistItem: async (taskId, text) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/checklist`, { text })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateChecklistItem: async (taskId, itemId, patch) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/checklist/${itemId}`, {
            text: patch?.text,
            done: patch?.done,
          })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateFocus: async (taskId, focus) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/focus`, { focus })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateTimeBudget: async (taskId, timeBudgetMinutes) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/time-budget`, { timeBudgetMinutes })
        );
        applyUpdatedTask(task);
        await after();
      },
      startTimer: async (taskId) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/timer/start`)
        );
        applyUpdatedTask(task);
        await after();
      },
      stopTimer: async (taskId, note) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/timer/stop`, { note: note || null })
        );
        applyUpdatedTask(task);
        await after();
      },
      addDecision: async (taskId, message) => {
        const task = await run(() =>
          api.post(`/api/tasks/${taskId}/decisions`, { message })
        );
        applyUpdatedTask(task);
        await after();
      },
      updateRecurrence: async (taskId, payload) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/recurrence`, payload)
        );
        applyUpdatedTask(task);
        await after();
      },
      updateDependencies: async (taskId, blockedByTaskIds) => {
        const task = await run(() =>
          api.put(`/api/tasks/${taskId}/dependencies`, { blockedByTaskIds })
        );
        applyUpdatedTask(task);
        await after();
      },
    }),
    [after, applyUpdatedTask, run]
  );
}
