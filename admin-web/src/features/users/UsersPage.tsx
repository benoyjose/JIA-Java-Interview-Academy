import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { UserAdminItem } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";

const ROLES = ["LEARNER", "EDITOR", "REVIEWER", "ADMIN"];

export function UsersPage() {
  const { user } = useAuth();
  const [items, setItems] = useState<UserAdminItem[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [drafts, setDrafts] = useState<Record<string, { status: string; roles: string[] }>>({});

  async function load() {
    setLoading(true);
    try {
      const result = await api.adminUsers({ q: query.trim() || undefined, page: 0, size: 100 });
      setItems(result.items);
      setDrafts(Object.fromEntries(result.items.map(item => [item.id, { status: item.status, roles: item.roles }])));
      setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load users."); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  function toggleRole(id: string, role: string) {
    setDrafts(current => {
      const draft = current[id] ?? { status: "ACTIVE", roles: [] };
      const roles = draft.roles.includes(role) ? draft.roles.filter(value => value !== role) : [...draft.roles, role];
      return { ...current, [id]: { ...draft, roles } };
    });
  }
  async function save(item: UserAdminItem) {
    const draft = drafts[item.id];
    if (!draft?.roles.length) { setError("Every account must retain at least one role."); return; }
    setSaving(item.id); setError(""); setNotice("");
    try {
      await api.updateAdminUser(item.id, draft);
      setNotice(`Updated access for ${item.name}.`);
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not update user."); }
    finally { setSaving(null); }
  }

  return <section>
    <div className="page-heading"><div><p className="eyebrow">ACCESS GOVERNANCE</p><h1>User management</h1>
      <p className="muted">Manage academy account status and role assignments.</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {notice && <div className="notice success" role="status">{notice}</div>}
    <div className="panel">
      <div className="list-controls"><input aria-label="Search users" placeholder="Search name or email…" value={query}
        onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === "Enter") void load(); }} />
        <button className="button button-secondary" onClick={() => void load()}>Search</button></div>
      {loading ? <p className="muted">Loading users…</p> : <div className="table-wrap"><table className="data-table">
        <thead><tr><th>User</th><th>Status</th><th>Roles</th><th>Action</th></tr></thead>
        <tbody>{items.map(item => {
          const draft = drafts[item.id] ?? { status: item.status, roles: item.roles };
          const isSelf = item.id === user?.id;
          return <tr key={item.id}><td><strong>{item.name}</strong><small>{item.email ?? "No email linked"}</small></td>
            <td><select aria-label={`Status for ${item.name}`} value={draft.status} disabled={isSelf}
              onChange={event => setDrafts(current => ({ ...current, [item.id]: { ...draft, status: event.target.value } }))}>
              {["ACTIVE", "LOCKED", "DISABLED"].map(value => <option key={value}>{value}</option>)}</select></td>
            <td><div className="role-checks">{ROLES.map(role => <label key={role}><input type="checkbox" checked={draft.roles.includes(role)}
              onChange={() => toggleRole(item.id, role)} />{role}</label>)}</div></td>
            <td><button className="button button-secondary" disabled={saving === item.id || (!draft.roles.length)}
              onClick={() => void save(item)}>{saving === item.id ? "Saving…" : "Save"}</button></td>
          </tr>;
        })}</tbody></table></div>}
    </div>
    <p className="muted small-copy">The final active administrator cannot be demoted. You cannot disable your own account.</p>
  </section>;
}
