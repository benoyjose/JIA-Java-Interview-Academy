import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { CategoryItem, TechnologyItem } from "../../api/types";
import { buildCategoryTree } from "./categoryTree";

const empty = { technologySlug: "", parentId: "", slug: "", name: "", sortOrder: "0" };

export function TaxonomyPage() {
  const [technologies, setTechnologies] = useState<TechnologyItem[]>([]);
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [technology, setTechnology] = useState("");
  const [categorySearch, setCategorySearch] = useState("");
  const [categoryStatus, setCategoryStatus] = useState("ALL");
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [technologyForm, setTechnologyForm] = useState({ slug: "", name: "", description: "", sortOrder: "0" });
  const [editingTechnologyId, setEditingTechnologyId] = useState<string | null>(null);
  const [technologySaving, setTechnologySaving] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [techs, cats] = await Promise.all([api.taxonomyTechnologies(), api.taxonomyCategories()]);
      setTechnologies(techs);
      setCategories(cats);
      if (!technology && techs.length) setTechnology(techs[0].slug);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not load taxonomy.");
    } finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);
  const visible = categories.filter(item => item.technologySlug === technology);
  const categoryRows = buildCategoryTree(visible, categorySearch, categoryStatus);
  const parents = visible.filter(item => item.id !== editingId && item.status !== "ARCHIVED");

  function edit(item: CategoryItem) {
    setEditingId(item.id);
    setForm({ technologySlug: item.technologySlug, parentId: item.parentId ?? "",
      slug: item.slug, name: item.name, sortOrder: String(item.sortOrder) });
    setNotice("");
  }
  function reset() { setEditingId(null); setForm({ ...empty, technologySlug: technology }); }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true); setError(""); setNotice("");
    const payload = { technologySlug: form.technologySlug, parentId: form.parentId || null,
      slug: form.slug.trim(), name: form.name.trim(), sortOrder: Number(form.sortOrder) };
    try {
      if (editingId) await api.updateCategory(editingId, payload);
      else await api.createCategory(payload);
      setNotice(editingId ? "Category updated." : "Category created.");
      reset();
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not save category."); }
    finally { setSaving(false); }
  }

  async function archive(item: CategoryItem) {
    if (!window.confirm(`Archive category "${item.name}"? Categories with lessons, questions or child categories cannot be archived.`)) return;
    setError(""); setNotice("");
    try { await api.archiveCategory(item.id); setNotice("Category archived."); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not archive category."); }
  }

  function editTechnology(item: TechnologyItem) {
    setEditingTechnologyId(item.id);
    setTechnologyForm({ slug: item.slug, name: item.name, description: item.description ?? "", sortOrder: String(item.sortOrder) });
  }
  function resetTechnology() {
    setEditingTechnologyId(null);
    setTechnologyForm({ slug: "", name: "", description: "", sortOrder: "0" });
  }
  async function saveTechnology(event: React.FormEvent) {
    event.preventDefault();
    setTechnologySaving(true); setError(""); setNotice("");
    const payload = { slug: technologyForm.slug.trim(), name: technologyForm.name.trim(),
      description: technologyForm.description.trim() || null, sortOrder: Number(technologyForm.sortOrder) };
    try {
      if (editingTechnologyId) await api.updateTechnology(editingTechnologyId, payload);
      else await api.createTechnology(payload);
      setNotice(editingTechnologyId ? "Technology updated." : "Technology created.");
      resetTechnology();
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not save technology."); }
    finally { setTechnologySaving(false); }
  }
  async function archiveTechnology(item: TechnologyItem) {
    if (!window.confirm("Archive technology " + item.name + "? All categories must be archived or moved first.")) return;
    setError(""); setNotice("");
    try { await api.archiveTechnology(item.id); setNotice("Technology archived."); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not archive technology."); }
  }

  return <section>
    <div className="page-heading"><div><p className="eyebrow">CONTENT GOVERNANCE</p><h1>Taxonomy</h1>
      <p className="muted">Manage technologies and the category hierarchy used by lessons and assessments.</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {notice && <div className="notice success" role="status">{notice}</div>}
    <div className="panel technology-admin">
      <div className="panel-heading"><h2>Technologies</h2><span className="muted">{technologies.length} records</span></div>
      <div className="technology-list">{technologies.map(item => <div className="technology-item" key={item.id}>
        <div><strong>{item.name}</strong><small>{item.slug} · {item.status}</small></div>
        <div><button className="text-button" onClick={() => editTechnology(item)} disabled={item.status === "ARCHIVED"}>Edit</button>
          <button className="text-button danger-text" onClick={() => void archiveTechnology(item)} disabled={item.status === "ARCHIVED"}>Archive</button></div>
      </div>)}</div>
      <form className="technology-form" onSubmit={event => void saveTechnology(event)}>
        <h3>{editingTechnologyId ? "Edit technology" : "Add technology"}</h3>
        <div className="technology-fields">
          <label>Name<input required maxLength={140} value={technologyForm.name} onChange={event => setTechnologyForm(v => ({ ...v, name: event.target.value }))} /></label>
          <label>Slug<input required maxLength={80} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={technologyForm.slug} onChange={event => setTechnologyForm(v => ({ ...v, slug: event.target.value }))} /></label>
          <label>Description<input maxLength={500} value={technologyForm.description} onChange={event => setTechnologyForm(v => ({ ...v, description: event.target.value }))} /></label>
          <label>Sort order<input type="number" min="0" max="10000" value={technologyForm.sortOrder} onChange={event => setTechnologyForm(v => ({ ...v, sortOrder: event.target.value }))} /></label>
        </div>
        <div className="row-actions"><button className="button button-primary" disabled={technologySaving}>{technologySaving ? "Saving…" : editingTechnologyId ? "Save technology" : "Create technology"}</button>
          {editingTechnologyId && <button type="button" className="button button-secondary" onClick={resetTechnology}>Cancel</button>}</div>
      </form>
    </div>
    <div className="editor-layout">
      <div className="panel">
        <div className="panel-heading"><h2>Categories</h2><span className="muted">{categoryRows.length} shown · {visible.length} total</span></div>
        <label className="field-label">Technology</label>
        <select value={technology} onChange={event => setTechnology(event.target.value)}>
          {technologies.map(item => <option key={item.id} value={item.slug}>{item.name}</option>)}
        </select>
        <div className="taxonomy-filters"><label>Search categories<input type="search" value={categorySearch} onChange={event => setCategorySearch(event.target.value)} placeholder="Name, slug or status" /></label><label>Status<select value={categoryStatus} onChange={event => setCategoryStatus(event.target.value)}><option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="ARCHIVED">Archived</option></select></label></div>
        {loading ? <p className="muted">Loading taxonomy…</p> : visible.length === 0 ?
          <p className="muted">No categories found for this technology.</p> :
          {categoryRows.length === 0 ? <p className="muted">No categories match these filters.</p> : <div className="table-wrap"><table className="data-table"><thead><tr>
            <th>Category</th><th>Slug</th><th>Content</th><th>Status</th><th>Actions</th>
          </tr></thead><tbody>{visible.map(item => <tr key={item.id}>
            <td><strong>{item.parentSlug ? "↳ " : ""}{item.name}</strong></td><td><code>{item.slug}</code></td>
            <td>{item.lessonCount} lessons · {item.questionCount} questions</td><td>{item.status}</td>
            <td><button className="text-button" onClick={() => edit(item)} disabled={item.status === "ARCHIVED"}>Edit</button>
              <button className="text-button danger-text" onClick={() => void archive(item)} disabled={item.status === "ARCHIVED"}>Archive</button></td>
          </tr>)}</tbody></table></div>}
      </div>
      <form className="panel editor-form" onSubmit={event => void save(event)}>
        <div className="panel-heading"><h2>{editingId ? "Edit category" : "New category"}</h2>
          {editingId && <button type="button" className="text-button" onClick={reset}>Cancel edit</button>}</div>
        <label>Technology<select required value={form.technologySlug} onChange={event => setForm(v => ({ ...v, technologySlug: event.target.value, parentId: "" }))}>
          {technologies.filter(item => item.status !== "ARCHIVED").map(item => <option key={item.id} value={item.slug}>{item.name}</option>)}
        </select></label>
        <label>Parent category (optional)<select value={form.parentId} onChange={event => setForm(v => ({ ...v, parentId: event.target.value }))}>
          <option value="">Top-level category</option>{parents.filter(item => item.technologySlug === form.technologySlug).map(item =>
            <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>
        <label>Category name<input required maxLength={160} value={form.name} onChange={event => setForm(v => ({ ...v, name: event.target.value }))} /></label>
        <label>URL slug<input required maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={form.slug} onChange={event => setForm(v => ({ ...v, slug: event.target.value }))} /></label>
        <label>Sort order<input type="number" min="0" max="10000" value={form.sortOrder} onChange={event => setForm(v => ({ ...v, sortOrder: event.target.value }))} /></label>
        <button className="button button-primary" disabled={saving || !form.technologySlug}>{saving ? "Saving…" : editingId ? "Save changes" : "Create category"}</button>
        <p className="muted small-copy">Categories are archived rather than deleted. Existing lesson and question references are protected.</p>
      </form>
    </div>
  </section>;
}
