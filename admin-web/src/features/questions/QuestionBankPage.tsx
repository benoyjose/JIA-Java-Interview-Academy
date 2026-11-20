import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { QuestionAdminItem } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";

const PAGE_SIZE = 25;
const nextStatus: Record<string, string> = {
  DRAFT: "IN_REVIEW", IN_REVIEW: "APPROVED", APPROVED: "PUBLISHED",
  PUBLISHED: "ARCHIVED", ARCHIVED: "DRAFT",
};
const sample = JSON.stringify({ formatVersion: 1, questions: [] }, null, 2);

export function QuestionBankPage() {
  const { user } = useAuth();
  const canCreate = Boolean(user?.roles.some(role => role === "EDITOR" || role === "ADMIN"));
  const canReview = Boolean(user?.roles.some(role => role === "REVIEWER" || role === "ADMIN"));
  const canPublish = Boolean(user?.roles.includes("ADMIN"));
  const [items, setItems] = useState<QuestionAdminItem[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [importJson, setImportJson] = useState(sample);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function load() {
    setLoading(true);
    try {
      const result = await api.adminQuestions({ q: query.trim() || undefined, status: status || undefined,
        difficulty: difficulty || undefined, page, size: PAGE_SIZE });
      setItems(result.items); setTotalPages(result.totalPages); setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load question bank."); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [query, status, difficulty, page]);

  async function transition(item: QuestionAdminItem) {
    const target = nextStatus[item.status];
    if (!target || !window.confirm(`Move "${item.slug}" from ${item.status} to ${target}?`)) return;
    setBusy(item.id); setError(""); setNotice("");
    try { await api.transitionQuestion(item.id, target); setNotice(`Question moved to ${target}.`); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not update question status."); }
    finally { setBusy(null); }
  }

  async function importBatch(event: React.FormEvent) {
    event.preventDefault(); setBusy("import"); setError(""); setNotice("");
    try {
      const payload: unknown = JSON.parse(importJson);
      const result = await api.importQuestions(payload);
      setNotice(`Import completed: ${String(result.importedCount ?? 0)} question(s).`);
      setImportOpen(false); setImportJson(sample); await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Import failed. Check the JSON format."); }
    finally { setBusy(null); }
  }

  return <section>
    <div className="page-heading"><div><p className="eyebrow">ASSESSMENT CONTENT</p><h1>Question bank</h1>
      <p className="muted">Review, publish and maintain versioned interview questions.</p></div>
      {canCreate && <button className="button button-primary" onClick={() => setImportOpen(value => !value)}>＋ Import questions</button>}</div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {notice && <div className="notice success" role="status">{notice}</div>}
    {importOpen && canCreate && <form className="panel editor-form" onSubmit={event => void importBatch(event)}>
      <div className="panel-heading"><h2>Import question batch</h2><button type="button" className="text-button" onClick={() => setImportOpen(false)}>Close</button></div>
      <p className="muted small-copy">Use formatVersion 1 with 1–100 questions. Each record must include categorySlug, slug, questionType, difficulty, prompt, options, answer, explanation and provenance.</p>
      <textarea className="code-input" rows={12} spellCheck={false} value={importJson} onChange={event => setImportJson(event.target.value)} />
      <button className="button button-primary" disabled={busy === "import"}>{busy === "import" ? "Importing…" : "Validate & import"}</button>
    </form>}
    <div className="panel">
      <div className="list-controls"><input aria-label="Search questions" placeholder="Search question, slug, category or technology…" value={query}
        onChange={event => { setQuery(event.target.value); setPage(0); }} />
        <select aria-label="Question status" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">All statuses</option>{["DRAFT","IN_REVIEW","APPROVED","PUBLISHED","ARCHIVED"].map(value => <option key={value}>{value}</option>)}
        </select>
        <select aria-label="Difficulty" value={difficulty} onChange={event => { setDifficulty(event.target.value); setPage(0); }}>
          <option value="">All levels</option>{["BEGINNER","NOVICE","PROFESSIONAL","EXPERT"].map(value => <option key={value}>{value}</option>)}
        </select></div>
      {loading ? <p className="muted">Loading questions…</p> : items.length === 0 ? <div className="empty-state"><strong>No questions found</strong><p>Import a question batch or adjust the filters.</p></div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Question</th><th>Technology / category</th><th>Level</th><th>Status</th><th>Revision</th><th>Workflow</th></tr></thead>
          <tbody>{items.map(item => <tr key={item.id}><td><strong>{item.promptText || item.slug}</strong><small>{item.slug} · {item.questionType}</small></td>
            <td>{item.technologyName}<small>{item.categoryName}</small></td><td>{item.difficulty}</td>
            <td><span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status.replace("_", " ")}</span></td>
            <td>v{item.revisionNumber}</td><td>{((item.status === "DRAFT" || item.status === "ARCHIVED") && canCreate)
              || (item.status === "IN_REVIEW" && canReview)
              || ((item.status === "APPROVED" || item.status === "PUBLISHED") && canPublish)
              ? <button className="text-button" disabled={busy !== null} onClick={() => void transition(item)}>{busy === item.id ? "Updating…" : nextStatus[item.status]}</button>
              : <span className="muted">—</span>}</td>
          </tr>)}</tbody></table></div>}
      {totalPages > 1 && <div className="page-controls"><button className="button button-secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
        <span>Page {page + 1} of {totalPages}</span><button className="button button-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Next</button></div>}
    </div>
  </section>;
}
