import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { ReviewDetail, ReviewQueueItem } from "../../api/types";

export function ReviewsPage() {
  const [queue, setQueue] = useState<ReviewQueueItem[]>([]); const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ReviewDetail | null>(null); const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  function reload() {
    setLoading(true);
    api.reviews().then(items => { setQueue(items); setError(""); setSelectedId(current => current && items.some(item => item.taskId === current) ? current : items[0]?.taskId ?? null); })
      .catch(reason => setError(reason instanceof Error ? reason.message : "Could not load review queue."))
      .finally(() => setLoading(false));
  }
  useEffect(() => { reload(); }, []);
  useEffect(() => {
    if (!selectedId) { setDetail(null); return; }
    const controller = new AbortController();
    api.review(selectedId, controller.signal).then(setDetail)
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load submitted revision."); });
    return () => controller.abort();
  }, [selectedId]);
  async function decide(decision: "APPROVE" | "REQUEST_CHANGES" | "REJECT") {
    if (!selectedId) return; setBusy(true); setError("");
    try {
      await api.decideReview(selectedId, decision, comment);
      setNotice(decision === "APPROVE" ? "Revision approved." : decision === "REJECT" ? "Revision rejected." : "Changes requested.");
      setComment(""); setDetail(null); setSelectedId(null); reload();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Review decision failed."); }
    finally { setBusy(false); }
  }
  return <section>
    <div className="page-heading"><div><p className="eyebrow">CONTENT GOVERNANCE</p><h1>Review queue</h1><p className="muted">Inspect the exact submitted revision before recording a decision.</p></div><button className="button button-secondary" onClick={reload}>↻ Refresh</button></div>
    {notice && <div className="notice success" role="status">{notice}</div>}{error && <div className="notice error" role="alert">{error}</div>}
    <div className="content-grid review-grid">
      <div className="panel lesson-list-panel"><div className="panel-heading"><div><h2>Pending reviews</h2><span className="muted small-copy">Oldest submissions first</span></div><span className="count-pill">{queue.length}</span></div>
        {loading ? <div className="loading-line">Loading review queue…</div> : queue.length === 0 ? <div className="empty-state"><span>✓</span><strong>Queue is clear</strong><p>No pending lesson reviews.</p></div> :
          <div className="lesson-list">{queue.map(item => <button key={item.taskId} className={selectedId === item.taskId ? "lesson-row selected" : "lesson-row"} onClick={() => setSelectedId(item.taskId)}>
            <span className="lesson-row-main"><strong>{item.title}</strong><small>{item.slug} · by {item.submittedBy}</small><small>{new Date(item.submittedAt).toLocaleString()}</small></span><span className="status-badge status-in_review">PENDING</span><span className="row-chevron">›</span>
          </button>)}</div>}
      </div>
      <div className="panel editor-panel">{!detail ? <div className="empty-editor"><div className="empty-icon">✓</div><h2>Review a submission</h2><p>Select a pending item to inspect its immutable revision snapshot.</p></div> :
        <div className="editor-content"><div className="panel-heading"><div><p className="eyebrow">SUBMITTED REVISION · V{detail.revisionNumber}</p><h2>{detail.title}</h2><span className="muted small-copy">{detail.slug} · submitted by {detail.submittedBy}</span></div></div>
          <div className="review-meta"><span>{detail.difficulty}</span><span>{detail.sourceType}</span><span>{new Date(detail.submittedAt).toLocaleString()}</span></div>
          {detail.summary && <p className="review-summary">{detail.summary}</p>}{detail.changeNote && <div className="notice info">Author note: {detail.changeNote}</div>}
          <h3>Content snapshot</h3><pre className="json-preview">{formatJson(detail.contentJson)}</pre>
          <label>Reviewer comment<textarea rows={3} maxLength={2000} value={comment} onChange={event => setComment(event.target.value)} placeholder="Add feedback for the author (optional for approval)." /></label>
          <div className="editor-actions review-actions"><button className="button button-danger" disabled={busy} onClick={() => void decide("REJECT")}>Reject</button><button className="button button-secondary" disabled={busy} onClick={() => void decide("REQUEST_CHANGES")}>Request changes</button><button className="button button-primary" disabled={busy} onClick={() => void decide("APPROVE")}>Approve revision</button></div>
        </div>}
      </div>
    </div>
  </section>;
}
function formatJson(raw: string): string { try { return JSON.stringify(JSON.parse(raw), null, 2); } catch { return raw; } }