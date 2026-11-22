import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api, validateLessonContent } from "../../api/client";
import type { AdminLesson, ContentRelease, LessonDetail, LessonDraftRequest, LessonStatus, PageResult, RevisionHistoryItem } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";

const PAGE_SIZE = 25;
const statuses: Array<LessonStatus | "ALL"> = ["ALL", "DRAFT", "IN_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED"];
const emptyPage: PageResult<AdminLesson> = { items: [], page: 0, size: PAGE_SIZE, totalItems: 0, totalPages: 0 };
const emptyContent = JSON.stringify({ schemaVersion: 1, blocks: [] }, null, 2);

export function LessonsPage() {
  const { user } = useAuth();
  const [result, setResult] = useState(emptyPage);
  const [status, setStatus] = useState("ALL");
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<LessonDetail | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [notice, setNotice] = useState("");
  const [releaseBusy, setReleaseBusy] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    api.lessons({ status: status === "ALL" ? undefined : status, q: debouncedSearch || undefined, page, size: PAGE_SIZE }, controller.signal)
      .then(data => { setResult(data); setError(""); })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load lessons."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, debouncedSearch, page, refreshKey]);

  useEffect(() => {
    if (!selectedId) return;
    const controller = new AbortController();
    setSelected(null);
    setError("");
    api.lesson(selectedId, controller.signal).then(detail => {
      if (!controller.signal.aborted) { setSelected(detail); setCreating(false); }
    }).catch(reason => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load lesson.");
    });
    return () => controller.abort();
  }, [selectedId, refreshKey]);

  function startCreate() { setSelectedId(null); setSelected(null); setCreating(true); setNotice(""); }
  async function onSaved(id: string, message: string) {
    setSelectedId(id); setCreating(false); setNotice(message); setRefreshKey(value => value + 1);
  }
  async function publish(id: string) {
    setNotice("");
    try { await api.publishLesson(id); setSelectedId(id); setRefreshKey(value => value + 1); setNotice("Lesson published successfully."); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Publishing failed."); }
  }
  async function publishContentRelease() {
    setReleaseBusy(true); setError(""); setNotice("");
    try {
      const release: ContentRelease = await api.createContentRelease();
      setNotice(`Content release v${release.version} published with ${release.items.length} lesson(s).`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not publish content release.");
    } finally { setReleaseBusy(false); }
  }
  function changeStatus(value: string) {
    setStatus(value); setPage(0); setSelectedId(null); setSelected(null);
  }
  function changeSearch(value: string) {
    setSearch(value); setPage(0); setSelectedId(null); setSelected(null);
  }

  return <section>
    <div className="page-heading"><div><p className="eyebrow">CONTENT LIBRARY</p><h1>Lessons</h1><p className="muted">Create, revise and publish structured Java learning content.</p></div><div className="page-actions">{user?.roles.includes("ADMIN") && <button className="button button-secondary" disabled={releaseBusy} onClick={() => void publishContentRelease()}>{releaseBusy ? "Publishing…" : "Publish content release"}</button>}<button className="button button-primary" onClick={startCreate}>＋ New lesson</button></div></div>
    <div className="stat-grid">
      <Stat label="Matching lessons" value={result.totalItems} note="Server-side total" />
      <Stat label="Current page" value={result.totalPages ? page + 1 : 0} note={`of ${result.totalPages}`} />
      <Stat label="Page size" value={PAGE_SIZE} note="Rows per request" />
      <Stat label="Status filter" value={status === "ALL" ? "All" : status.replace("_", " ")} note="Applied on server" />
    </div>
    {notice && <div className="notice success" role="status">{notice}</div>}
    {error && <div className="notice error" role="alert">{error}<button className="text-button" onClick={() => setRefreshKey(value => value + 1)}>Retry</button></div>}
    <div className="content-grid">
      <div className="panel lesson-list-panel">
        <div className="panel-heading"><div><h2>All lessons</h2><span className="muted small-copy">Server-filtered · newest updates first</span></div><span className="count-pill">{result.totalItems.toLocaleString()}</span></div>
        <div className="list-controls">
          <input aria-label="Search lessons" placeholder="Start of title, slug or category…" maxLength={120} value={search} onChange={event => changeSearch(event.target.value)} />
          <select aria-label="Filter by status" value={status} onChange={event => changeStatus(event.target.value)}>{statuses.map(item => <option key={item} value={item}>{item === "ALL" ? "All statuses" : item.replace("_", " ")}</option>)}</select>
        </div>
        {loading ? <div className="loading-line">Loading lessons…</div> : result.items.length === 0 ? <div className="empty-state"><span>▤</span><strong>No lessons found</strong><p>Try another search or create a lesson draft.</p></div> :
          <div className="lesson-list">{result.items.map(item => <button key={item.id} className={selectedId === item.id ? "lesson-row selected" : "lesson-row"} onClick={() => { setSelected(null); setSelectedId(item.id); setCreating(false); setNotice(""); }}>
            <span className="lesson-row-main"><strong>{item.title}</strong><small>{item.categorySlug} / {item.slug}</small></span><span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status.replace("_", " ")}</span><span className="row-chevron">›</span>
          </button>)}</div>}
        <PageControls page={page} totalPages={result.totalPages} loading={loading} onChange={next => { setPage(next); setSelectedId(null); setSelected(null); }} />
      </div>
      <div className="panel editor-panel">{creating ? <LessonEditor onSaved={onSaved} onCancel={() => setCreating(false)} /> :
        selected ? <LessonEditor key={selected.id + selected.revisionNumber + String(selected.editingDraft)} lesson={selected} onSaved={onSaved} onCancel={() => { setSelected(null); setSelectedId(null); }} onPublish={publish} /> :
        <div className="empty-editor"><div className="empty-icon">✎</div><h2>Select a lesson</h2><p>Choose a lesson to inspect its current revision or start a new draft.</p></div>}</div>
    </div>
  </section>;
}

function Stat({ label, value, note }: { label: string; value: number | string; note: string }) {
  return <div className="stat-card"><span>{label}</span><strong>{typeof value === "number" ? value.toLocaleString() : value}</strong><small>{note}</small></div>;
}

function PageControls({ page, totalPages, loading, onChange }: {
  page: number; totalPages: number; loading: boolean; onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return <div className="page-controls"><button className="button button-secondary" disabled={loading || page === 0} onClick={() => onChange(page - 1)}>Previous</button>
    <span>Page {page + 1} of {totalPages}</span><button className="button button-secondary" disabled={loading || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button></div>;
}

function LessonEditor({ lesson, onSaved, onCancel, onPublish }: {
  lesson?: LessonDetail; onSaved: (id: string, message: string) => Promise<void>;
  onCancel: () => void; onPublish?: (id: string) => Promise<void>;
}) {
  const [categorySlug, setCategorySlug] = useState(lesson?.categorySlug ?? "");
  const [slug, setSlug] = useState(lesson?.slug ?? "");
  const [title, setTitle] = useState(lesson?.title ?? "");
  const [summary, setSummary] = useState(lesson?.summary ?? "");
  const [difficulty, setDifficulty] = useState<LessonDraftRequest["difficulty"]>(lesson?.difficulty ?? "BEGINNER");
  const [sourceType, setSourceType] = useState<LessonDraftRequest["sourceType"]>(lesson?.sourceType ?? "ADMIN_AUTHORED");
  const [contentJson, setContentJson] = useState(lesson?.contentJson ?? emptyContent);
  const [changeNote, setChangeNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [history, setHistory] = useState<RevisionHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [rollbackBusy, setRollbackBusy] = useState<number | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  useEffect(() => {
    if (!lesson) return;
    const controller = new AbortController();
    setHistoryLoading(true);
    api.lessonHistory(lesson.id, controller.signal)
      .then(items => setHistory(items))
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Could not load revision history."); })
      .finally(() => { if (!controller.signal.aborted) setHistoryLoading(false); });
    return () => controller.abort();
  }, [lesson?.id]);

  async function rollback(revisionNumber: number) {
    if (!lesson || !window.confirm(`Create a new draft from revision ${revisionNumber}? Published content will remain live until review and publication.`)) return;
    setRollbackBusy(revisionNumber); setError("");
    try {
      await api.rollbackLesson(lesson.id, revisionNumber, `Restored from revision ${revisionNumber}`);
      await onSaved(lesson.id, `Rollback draft created from revision ${revisionNumber}. Submit it for review before publishing.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Rollback failed."); }
    finally { setRollbackBusy(null); }
  }

  async function save(event: FormEvent<HTMLFormElement>, submitForReview = false) {
    event.preventDefault();
    const invalidContent = validateLessonContent(contentJson);
    if (invalidContent) { setError(invalidContent); return; }
    setBusy(true); setError("");
    const payload: LessonDraftRequest = {
      categorySlug: categorySlug.trim(), slug: slug.trim(), title: title.trim(),
      summary: summary.trim() || null, difficulty, sourceType, contentJson, changeNote: changeNote.trim() || null,
    };
    try {
      const result = lesson ? await api.updateLesson(lesson.id, payload) : await api.createLesson(payload);
      if (submitForReview) {
        await api.submitLesson(result.id);
        await onSaved(result.id, "Draft saved and submitted for review.");
      } else await onSaved(result.id, "Draft saved as revision " + result.revisionNumber + ".");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not save lesson."); }
    finally { setBusy(false); }
  }
  const canSubmit = lesson ? lesson.status === "DRAFT" || (lesson.status === "PUBLISHED" && lesson.editingDraft) : true;
  const canPublish = lesson?.status === "APPROVED";

  return <div className="editor-content">
    <div className="panel-heading"><div><p className="eyebrow">{lesson ? "LESSON REVISION" : "NEW CONTENT"}</p><h2>{lesson ? lesson.title : "Create lesson"}</h2><span className="muted small-copy">{lesson ? `Revision ${lesson.revisionNumber} · ${lesson.status}` : "Unpublished draft"}</span></div><button className="icon-button" onClick={onCancel} aria-label="Close editor">×</button></div>
    {lesson?.status === "PUBLISHED" && <div className="notice info">Live revision {lesson.publishedRevision ?? "—"} remains visible while you prepare this draft.</div>}
    {lesson && <div className="revision-history">
      <div className="panel-heading"><h3>Revision & review history</h3><span className="muted small-copy">{history.length} revision(s)</span></div>
      {historyLoading ? <p className="muted small-copy">Loading history…</p> : history.length === 0 ? <p className="muted small-copy">No revision history available.</p> :
        <ol className="revision-list">{history.map(item => <li key={item.revisionId}>
          <div><strong>Revision {item.revisionNumber}: {item.title}</strong>
            <small>{new Date(item.createdAt).toLocaleString()} · {item.changedByName}{item.published ? " · Published" : item.currentDraft ? " · Current draft" : ""}</small>
            {item.reviewStatus && <small>Review: {item.reviewStatus.replace("_", " ")}{item.submittedBy ? ` · submitted by ${item.submittedBy}` : ""}</small>}
            {item.changeNote && <small>{item.changeNote}</small>}
            {item.reviewComment && <small>Reviewer: {item.reviewComment}</small>}
          </div>
          {["DRAFT", "PUBLISHED"].includes(lesson.status) && !lesson.editingDraft && !item.currentDraft && item.revisionNumber !== lesson.revisionNumber &&
            <button type="button" className="text-button" disabled={rollbackBusy !== null} onClick={() => void rollback(item.revisionNumber)}>{rollbackBusy === item.revisionNumber ? "Restoring…" : "Restore"}</button>}
        </li>)}</ol>}
    </div>}
    <form className="editor-form" onSubmit={event => void save(event)}>
      <label>Lesson title<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
      <div className="field-row"><label>Category slug<input required maxLength={100} placeholder="java-language-fundamentals" value={categorySlug} onChange={event => setCategorySlug(event.target.value)} /></label><label>Lesson slug<input required maxLength={120} value={slug} onChange={event => setSlug(event.target.value)} /></label></div>
      <label>Summary<textarea rows={2} maxLength={700} value={summary} onChange={event => setSummary(event.target.value)} /></label>
      <div className="field-row"><label>Difficulty<select value={difficulty} onChange={event => setDifficulty(event.target.value as LessonDraftRequest["difficulty"])}>{["BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT"].map(value => <option key={value}>{value}</option>)}</select></label><label>Source type<select value={sourceType} onChange={event => setSourceType(event.target.value as LessonDraftRequest["sourceType"])}>{["ADMIN_AUTHORED", "USER_ORIGINAL", "EXTERNAL_REFERENCE"].map(value => <option key={value}>{value}</option>)}</select></label></div>
      <label>Content document (JSON)<textarea className="code-input" rows={11} spellCheck={false} value={contentJson} onChange={event => setContentJson(event.target.value)} /></label>
      <button type="button" className="text-button" aria-expanded={previewOpen}
        onClick={() => setPreviewOpen(value => !value)}>
        {previewOpen ? "Hide learner preview" : "Preview as learner"}
      </button>
      {previewOpen && <LessonPreview contentJson={contentJson} />}
      <label>Change note<input maxLength={500} placeholder="What changed in this revision?" value={changeNote} onChange={event => setChangeNote(event.target.value)} /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="editor-actions">
        <button className="button button-secondary" type="button" onClick={onCancel}>Cancel</button>
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
type PreviewBlock = {
  type?: string; title?: string; body?: string | string[]; source?: string;
  language?: string; items?: string[]; url?: string; alt?: string; caption?: string;
  label?: string; expandedExplanation?: string; points?: string[];
};

function LessonPreview({ contentJson }: { contentJson: string }) {
  let blocks: PreviewBlock[];
  try {
    const document = JSON.parse(contentJson) as { schemaVersion?: number; blocks?: PreviewBlock[] };
    if (document.schemaVersion !== 1 || !Array.isArray(document.blocks)) {
      return <div className="notice error" role="alert">Preview unavailable: unsupported lesson document schema.</div>;
    }
    blocks = document.blocks;
  } catch {
    return <div className="notice error" role="alert">Preview unavailable: fix the JSON syntax first.</div>;
  }

  return <section className="lesson-preview" aria-label="Private learner preview">
    <div className="panel-heading"><h3>Learner preview</h3><span className="muted small-copy">Draft only · not publicly visible</span></div>
    {blocks.map((block, index) => {
      const key = `${block.type ?? "unknown"}-${index}`;
      const heading = block.title ? <h4>{block.title}</h4> : null;
      const body = typeof block.body === "string" ? block.body : null;
      switch (block.type) {
        case "markdown": case "section": case "overview": case "objectives":
        case "prerequisites": case "explanation": case "tradeoffs":
        case "commonMistakes": case "troubleshooting": case "followUps":
          return <article key={key}>{heading}{body && <p>{body}</p>}</article>;
        case "callout":
          return <aside key={key} className="notice info">{heading}{body}</aside>;
        case "code":
          return <article key={key}>{heading}<small>{block.language?.toUpperCase() ?? "CODE"}</small>
            <pre><code>{block.source ?? ""}</code></pre></article>;
        case "list":
          return <article key={key}>{heading}<ul>{(block.items ?? []).map((item, i) => <li key={i}>{item}</li>)}</ul></article>;
        case "interviewAnswer": {
          const points = Array.isArray(block.body) ? block.body : block.points ?? [];
          return <article key={key}>{heading}<ol>{points.map((point, i) => <li key={i}>{point}</li>)}</ol>
            {block.expandedExplanation && <p>{block.expandedExplanation}</p>}</article>;
        }
        case "diagram":
          return <article key={key}>{heading}<pre><code>{block.source ?? ""}</code></pre></article>;
        case "image":
          return block.url?.startsWith("https://") && block.alt
            ? <figure key={key}>{heading}<img src={block.url} alt={block.alt} loading="lazy" />
                {block.caption && <figcaption>{block.caption}</figcaption>}</figure> : null;
        case "video": case "resourceLink":
          return block.url?.startsWith("https://")
            ? <article key={key}>{heading}<a href={block.url} target="_blank" rel="noreferrer">{block.label ?? block.alt ?? "Open resource"}</a>
                {block.caption && <p>{block.caption}</p>}</article> : null;
        default:
          return null;
      }
    })}
    {blocks.length === 0 && <p className="muted">No lesson blocks to preview.</p>}
  </section>;
}

