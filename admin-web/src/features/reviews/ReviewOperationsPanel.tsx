import { useMemo, useState } from "react";

export type ReviewOperation = {
  taskId: string; lessonTitle: string; lessonSlug: string; submittedBy: string;
  submittedAt: string; status: "PENDING" | "IN_REVIEW" | "APPROVED" | "REJECTED";
};
type Props = { items: ReviewOperation[]; onOpen: (taskId: string) => void };

/** Reviewer workload view: deterministic age bands, search, status filter and queue summary. */
export function ReviewOperationsPanel({ items, onOpen }: Props) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("OPEN");
  const filtered = useMemo(() => items.filter(item => {
    const open = item.status === "PENDING" || item.status === "IN_REVIEW";
    const matchesStatus = status === "ALL" || (status === "OPEN" ? open : item.status === status);
    const text = (item.lessonTitle + " " + item.lessonSlug + " " + item.submittedBy).toLocaleLowerCase();
    return matchesStatus && text.includes(query.trim().toLocaleLowerCase());
  }), [items, query, status]);
  const now = Date.now();
  const openItems = items.filter(item => item.status === "PENDING" || item.status === "IN_REVIEW");
  const olderThanDay = openItems.filter(item => now - Date.parse(item.submittedAt) >= 86400000).length;
  const invalidDates = openItems.filter(item => !Number.isFinite(Date.parse(item.submittedAt))).length;
  return <section className="panel" aria-labelledby="review-ops-heading">
    <div className="panel-heading"><div><h2 id="review-ops-heading">Review operations</h2>
      <p className="muted small-copy">Queue health and reviewer workload. Age is based on submission time.</p></div>
      <span className="count-pill" aria-live="polite">{openItems.length} open</span></div>
    <div className="stat-grid">
      <article className="stat-card"><span>Pending</span><strong>{items.filter(x => x.status === "PENDING").length}</strong></article>
      <article className="stat-card"><span>In review</span><strong>{items.filter(x => x.status === "IN_REVIEW").length}</strong></article>
      <article className="stat-card"><span>Open over 24h</span><strong>{olderThanDay}</strong></article>
    </div>
    {invalidDates > 0 && <p className="notice error" role="status">{invalidDates} open item(s) have invalid submission timestamps.</p>}
    <div className="form-row">
      <label>Search queue<input value={query} onChange={e => setQuery(e.target.value)} placeholder="Lesson, slug, submitter" /></label>
      <label>Queue status<select value={status} onChange={e => setStatus(e.target.value)}>
        <option value="OPEN">Open</option><option value="PENDING">Pending</option>
        <option value="IN_REVIEW">In review</option><option value="APPROVED">Approved</option>
        <option value="REJECTED">Rejected</option><option value="ALL">All</option>
      </select></label>
    </div>
    {filtered.length === 0 ? <p className="muted">No review tasks match these filters.</p> : <ul className="review-ops-list">
      {filtered.map(item => <li key={item.taskId}><div><strong>{item.lessonTitle}</strong>
        <small>{item.lessonSlug} · {item.submittedBy} · {Number.isFinite(Date.parse(item.submittedAt)) ? new Date(item.submittedAt).toLocaleString() : "Date unavailable"}</small>
        </div><span className="status-pill">{item.status.replace("_", " ")}</span>
        <button className="button button-secondary" onClick={() => onOpen(item.taskId)}>Open review</button></li>)}
    </ul>}
  </section>;
}
