import { useEffect, useMemo, useRef, useState } from "react";

function normalize(s) {
  return String(s || "").toLowerCase();
}

export default function CommandPalette({ open, tasks, onClose, onOpenTask }) {
  const [q, setQ] = useState("");
  const inputRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => {
      setQ("");
      inputRef.current?.focus();
    }, 0);
    return () => clearTimeout(t);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e) => {
      if (e.key === "Escape") onClose?.();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  const results = useMemo(() => {
    const list = Array.isArray(tasks) ? tasks : [];
    const query = q.trim();
    if (!query) return list.slice(0, 20);

    const nq = normalize(query);
    return list
      .filter((t) => {
        const title = normalize(t.title);
        const desc = normalize(t.description);
        const labels = Array.isArray(t.labels) ? t.labels : [];
        const labelMatch = labels.some((l) => normalize(l).includes(nq));
        const comments = Array.isArray(t.comments) ? t.comments : [];
        const commentMatch = comments.some((c) =>
          normalize(c?.message).includes(nq)
        );
        const decisions = Array.isArray(t.decisions) ? t.decisions : [];
        const decisionMatch = decisions.some((d) =>
          normalize(d?.message).includes(nq)
        );
        return (
          title.includes(nq) ||
          desc.includes(nq) ||
          labelMatch ||
          commentMatch ||
          decisionMatch
        );
      })
      .slice(0, 20);
  }, [tasks, q]);

  if (!open) return null;

  return (
    <div
      className="paletteOverlay"
      role="dialog"
      aria-modal="true"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      <div className="paletteModal">
        <div className="paletteHeader">
          <input
            ref={inputRef}
            className="input"
            placeholder="Search tasks… (Esc to close)"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                const first = results[0];
                if (first) onOpenTask?.(first);
              }
            }}
          />
          <button type="button" className="mini" onClick={onClose}>
            ✕
          </button>
        </div>

        <div className="paletteList">
          {results.length === 0 ? (
            <div className="muted">No matches</div>
          ) : null}

          {results.map((t) => (
            <button
              key={t.id}
              type="button"
              className="paletteItem"
              onClick={() => onOpenTask?.(t)}
            >
              <div className="paletteItemMain">
                <strong className="paletteTitle">{t.title}</strong>
                <div className="muted small">
                  {t.status}
                  {t.dueDate ? ` • Due ${String(t.dueDate)}` : ""}
                </div>
              </div>
              <div className="paletteItemMeta">
                {(Array.isArray(t.labels) ? t.labels : [])
                  .slice(0, 2)
                  .map((l) => (
                    <span key={l} className="labelChip small">
                      {l}
                    </span>
                  ))}
              </div>
            </button>
          ))}
        </div>

        <div className="paletteFooter muted small">
          Tip: Press Ctrl+K / ⌘K anywhere
        </div>
      </div>
    </div>
  );
}
