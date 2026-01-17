import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

const STORAGE_KEY = "focusTimer:v1";

const FocusTimerContext = createContext(null);

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;

    const activeTaskId =
      typeof parsed.activeTaskId === "string" ? parsed.activeTaskId : null;
    const startedAt =
      typeof parsed.startedAt === "number" ? parsed.startedAt : null;
    const running = Boolean(parsed.running);

    if (!activeTaskId || !startedAt) return null;
    return { activeTaskId, startedAt, running };
  } catch {
    return null;
  }
}

export function FocusTimerProvider({ children }) {
  const stored = useMemo(() => readStored(), []);
  const [activeTaskId, setActiveTaskId] = useState(
    stored?.activeTaskId ?? null
  );
  const [startedAt, setStartedAt] = useState(stored?.startedAt ?? null);
  const [running, setRunning] = useState(stored?.running ?? false);

  const [now, setNow] = useState(() => Date.now());
  const intervalRef = useRef(null);

  useEffect(() => {
    if (running) {
      if (!intervalRef.current) {
        intervalRef.current = setInterval(() => setNow(Date.now()), 1000);
      }
    } else {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [running]);

  useEffect(() => {
    const payload =
      activeTaskId && startedAt ? { activeTaskId, startedAt, running } : null;
    if (!payload) {
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }, [activeTaskId, startedAt, running]);

  const elapsedSeconds = useMemo(() => {
    if (!activeTaskId || !startedAt) return 0;
    const base = Math.max(0, Math.floor((now - startedAt) / 1000));
    return running ? base : base; // keep stable even if paused
  }, [activeTaskId, startedAt, now, running]);

  const start = (taskId) => {
    if (!taskId) return;
    setActiveTaskId(taskId);
    setStartedAt(Date.now());
    setRunning(true);
  };

  const stop = () => {
    setRunning(false);
  };

  const resume = () => {
    if (!activeTaskId || !startedAt) return;
    setRunning(true);
  };

  const clear = () => {
    setRunning(false);
    setActiveTaskId(null);
    setStartedAt(null);
  };

  return (
    <FocusTimerContext.Provider
      value={{
        activeTaskId,
        startedAt,
        running,
        elapsedSeconds,
        start,
        stop,
        resume,
        clear,
      }}
    >
      {children}
    </FocusTimerContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useFocusTimer() {
  const ctx = useContext(FocusTimerContext);
  if (!ctx)
    throw new Error("useFocusTimer must be used within FocusTimerProvider");
  return ctx;
}
