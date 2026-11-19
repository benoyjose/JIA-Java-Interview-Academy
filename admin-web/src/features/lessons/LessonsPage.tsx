import { useDeferredValue, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { api, validateLessonContent } from "../../api/client";
import type { AdminLesson, LessonDetail, LessonDraftRequest, LessonStatus } from "../../api/types";

const statuses: Array<LessonStatus | "ALL"> = ["ALL", "DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED"];
const emptyContent = JSON.stringify({ schemaVersion: 1, blocks: [] }, null, 2);

export function LessonsPage() {
  const [lessons, setLessons] = useState<AdminLesson[]>([]); const [status, setStatus] = useState("ALL");
  const [search, setSearch] = useState(""); const deferredSearch = useDeferredValue(search.trim().toLowerCase());
  const [selected, setSelected] = useState<LessonDetail | null>(null); const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(""); const [refreshKey, setRefreshKey] = useState(0); const [notice, setNotice] = useState("");

  useEffect(() => {
    const controller = new AbortController(); setLoading(true);
    api.lessons(status === "ALL" ? undefined : status, controller.signal)
      .then(data => { setLessons(data); setError(""); })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load lessons."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, refreshKey]);

  useEffect(() => {
    if (!selectedId) return;
    const controller = new AbortController();
    api.lesson(selectedId, controller.signal).then(detail => { setSelected(detail); setCreating(false); })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load lesson."); });
    return () => controller.abort();
  }, [selectedId, refreshKey]);

  const visible = useMemo(() => lessons.filter(item =>
    !deferredSearch || [item.title, item.slug, item.categorySlug, item.status].some(value => value.toLowerCase().includes(deferredSearch))
  ), [lessons, deferredSearch]);
  const counts = useMemo(() => ({
    total: lessons.length, drafts: lessons.filter(item => item.status === "DRAFT").length,
    review: lessons.filter(item => item.status === "IN_REVIEW").length,
    published: lessons.filter(item => item.status === "PUBLISHED").length,
  }), [lessons]);

  function startCreate() { setSelectedId(null); setSelected(null); setCreating(true); setNotice(""); }
  async function onSaved(id: string, message: string) {
    setSelectedId(id); setCreating(false); setNotice(message); setRefreshKey(value => value + 1);
  }
  async function publish(id: string) {
    setNotice("");
    try { await api.publishLesson(id); setSelectedId(id); setRefreshKey(value => value + 1); setNotice("Lesson published successfully."); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Publishing failed."); }
  }

  return <section>
    <div className="page-heading"><div><p className="eyebrow">CONTENT LIBRARY</p><h1>Lessons</h1><p className="muted">Create, revise and publish structured Java learning content.</p></div><button className="button button-primary" onClick={startCreate}>＋ New lesson</button></div>
    <div className="stat-grid"><Stat label="Loaded lessons" value={counts.total} note="Latest 100 from API" /><Stat label="Drafts" value={counts.drafts} note="Ready for editing" /><Stat label="In review" value={counts.review} note="Awaiting reviewer" /><Stat label="Published" value={counts.published} note="Visible to learners" /></div>
    {notice && <div className="notice success" role="status">{notice}</div>}
    {error && <div className="notice error" role="alert">{error}<button className="text-button" onClick={() => setRefreshKey(value => value + 1)}>Retry</button></div>}
    <div className="content-grid">
      <div className="panel lesson-list-panel"><div className="panel-heading"><div><h2>All lessons</h2><span className="muted small-copy">Newest updates first · maximum 100</span></div><span className="count-pill">{visible.length}</span></div>
        <div className="list-controls"><input aria-label="Search lessons" placeholder="Search title, slug or category…" value={search} onChange={event => setSearch(event.target.value)} />
          <select aria-label="Filter by status" value={status} onChange={event => setStatus(event.target.value)}>{statuses.map(item => <option key={item} value={item}>{item === "ALL" ? "All statuses" : item.replace("_", " ")}</option>)}</select></div>
        {loading ? <div className="loading-line">Loading lessons…</div> : visible.length === 0 ? <div className="empty-state"><span>▤</span><strong>No lessons found</strong><p>Try another filter or create a lesson draft.</p></div> :
          <div className="lesson-list">{visible.map(item => <button key={item.id} className={selectedId === item.id ? "lesson-row selected" : "lesson-row"} onClick={() => { setSelected(null); setSelectedId(item.id); setCreating(false); setNotice(""); }}>
            <span className="lesson-row-main"><strong>{item.title}</strong><small>{item.categorySlug} / {item.slug}</small></span><span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status.replace("_", " ")}</span><span className="row-chevron">›</span>
          </button>)}</div>}
      </div>
      <div className="panel editor-panel">{creating ? <LessonEditor onSaved={onSaved} onCancel={() => setCreating(false)} /> :
        selected ? <LessonEditor key={selected.id + selected.revisionNumber + String(selected.editingDraft)} lesson={selected} onSaved={onSaved} onCancel={() => { setSelected(null); setSelectedId(null); }} onPublish={publish} /> :
        <div className="empty-editor"><div className="empty-icon">✎</div><h2>Select a lesson</h2><p>Choose a lesson to inspect its current revision or start a new draft.</p></div>}</div>
    </div>
  </section>;
}
function Stat({ label, value, note }: { label: string; value: number; note: string }) {
  return <div className="stat-card"><span>{label}</span><strong>{value.toLocaleString()}</strong><small>{note}</small></div>;
}
function LessonEditor({ lesson, onSaved, onCancel, onPublish }: {
  lesson?: LessonDetail; onSaved: (id: string, message: string) => Promise<void>;
  onCancel: () => void; onPublish?: (id: string) => Promise<void>;
}) {
  const [categorySlug, setCategorySlug] = useState(lesson?.categorySlug ?? ""); const [slug, setSlug] = useState(lesson?.slug ?? "");
  const [title, setTitle] = useState(lesson?.title ?? ""); const [summary, setSummary] = useState(lesson?.summary ?? "");
  const [difficulty, setDifficulty] = useState<LessonDraftRequest["difficulty"]>(lesson?.difficulty ?? "BEGINNER");
  const [sourceType, setSourceType] = useState<LessonDraftRequest["sourceType"]>(lesson?.sourceType ?? "ADMIN_AUTHORED");
  const [contentJson, setContentJson] = useState(lesson?.contentJson ?? emptyContent); const [changeNote, setChangeNote] = useState("");
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");

  async function save(event: FormEvent<HTMLFormElement>, submitForReview = false) {
    event.preventDefault(); const invalidContent = validateLessonContent(contentJson);
    if (invalidContent) { setError(invalidContent); return; }
    setBusy(true); setError("");
    const payload: LessonDraftRequest = { categorySlug: categorySlug.trim(), slug: slug.trim(), title: title.trim(),
      summary: summary.trim() || null, difficulty, sourceType, contentJson, changeNote: changeNote.trim() || null };
    try {
      const result = lesson ? await api.updateLesson(lesson.id, payload) : await api.createLesson(payload);
      if (submitForReview) { await api.submitLesson(result.id); await onSaved(result.id, "Draft saved and submitted for review."); }
      else await onSaved(result.id, "Draft saved as revision " + result.revisionNumber + ".");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not save lesson."); }
    finally { setBusy(false); }
  }
  const canSubmit = lesson ? lesson.status === "DRAFT" || (lesson.status === "PUBLISHED" && lesson.editingDraft) : true;
  const canPublish = lesson?.status === "APPROVED";
  return <div className="editor-content">
    <div className="panel-heading"><div><p className="eyebrow">{lesson ? "LESSON REVISION" : "NEW CONTENT"}</p><h2>{lesson ? lesson.title : "Create lesson"}</h2><span className="muted small-copy">{lesson ? `Revision ${lesson.revisionNumber} · ${lesson.status}` : "Unpublished draft"}</span></div><button className="icon-button" onClick={onCancel} aria-label="Close editor">×</button></div>
    {lesson?.status === "PUBLISHED" && <div className="notice info">Live revision {lesson.publishedRevision ?? "—"} remains visible while you prepare this draft.</div>}
    <form className="editor-form" onSubmit={event => void save(event)}>
      <label>Lesson title<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
      <div className="field-row"><label>Category slug<input required maxLength={100} placeholder="java-language-fundamentals" value={categorySlug} onChange={event => setCategorySlug(event.target.value)} /></label><label>Lesson slug<input required maxLength={120} value={slug} onChange={event => setSlug(event.target.value)} /></label></div>
      <label>Summary<textarea rows={2} maxLength={700} value={summary} onChange={event => setSummary(event.target.value)} /></label>
      <div className="field-row"><label>Difficulty<select value={difficulty} onChange={event => setDifficulty(event.target.value as LessonDraftRequest["difficulty"])}>{["BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT"].map(value => <option key={value}>{value}</option>)}</select></label><label>Source type<select value={sourceType} onChange={event => setSourceType(event.target.value as LessonDraftRequest["sourceType"])}>{["ADMIN_AUTHORED", "USER_ORIGINAL", "EXTERNAL_REFERENCE"].map(value => <option key={value}>{value}</option>)}</select></label></div>
      <label>Content document (JSON)<textarea className="code-input" rows={11} spellCheck={false} value={contentJson} onChange={event => setContentJson(event.target.value)} /></label>
      <label>Change note<input maxLength={500} placeholder="What changed in this revision?" value={changeNote} onChange={event => setChangeNote(event.target.value)} /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="editor-actions"><button className="button button-secondary" type="button" onClick={onCancel}>Cancel</button>
        <button className="button button-secondary" type="submit" disabled={busy}>{busy ? "Saving…" : "Save draft"}</button>
        {canSubmit && <button className="button button-primary" type="button" disabled={busy} onClick={event => {
          const form = event.currentTarget.form;
          if (form?.reportValidity()) void save({ preventDefault: () => {}, currentTarget: form } as FormEvent<HTMLFormElement>, true);
        }}>Save & submit</button>}
        {canPublish && <button className="button button-primary" type="button" disabled={busy} onClick={() => onPublish?.(lesson!.id)}>Publish approved</button>}
      </div>
    </form>
  </div>;
}