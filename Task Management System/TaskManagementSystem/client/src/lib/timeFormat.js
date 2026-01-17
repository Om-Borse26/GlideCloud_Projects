// Formats a duration in seconds as mm:ss or hh:mm:ss.
export function formatHhMmSs(totalSeconds) {
  const s = Math.max(0, totalSeconds | 0);
  const hh = Math.floor(s / 3600);
  const mm = Math.floor((s % 3600) / 60);
  const ss = s % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return hh > 0 ? `${hh}:${pad(mm)}:${pad(ss)}` : `${mm}:${pad(ss)}`;
}
