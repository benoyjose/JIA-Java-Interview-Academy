import type { PropsWithChildren } from "react";
import { useAuth } from "../auth/AuthContext";
type Tab = "dashboard" | "lessons" | "reviews" | "taxonomy" | "questions" | "users";
export function AppShell({ activeTab, onNavigate, children }: PropsWithChildren<{ activeTab: Tab; onNavigate: (tab: Tab) => void }>) {
  const { user, logout } = useAuth();
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to main content</a>
    <aside className="sidebar" aria-label="Workspace navigation"><div className="sidebar-brand"><div className="brand-mark small">J</div><div><strong>JIA Admin</strong><span>Academy workspace</span></div></div>
      <div className="sidebar-label">WORKSPACE</div><nav className="side-nav" aria-label="Main navigation">
        {user?.roles.includes("ADMIN") &&
          <button className={activeTab === "dashboard" ? "nav-item active" : "nav-item"} aria-current={activeTab === "dashboard" ? "page" : undefined} onClick={() => onNavigate("dashboard")}><span aria-hidden="true">▦</span> Dashboard</button>}
        {user?.roles.some(role => role === "EDITOR" || role === "ADMIN") &&
          <button className={activeTab === "lessons" ? "nav-item active" : "nav-item"} aria-current={activeTab === "lessons" ? "page" : undefined} onClick={() => onNavigate("lessons")}><span aria-hidden="true">▤</span> Lessons</button>}
        {user?.roles.some(role => role === "REVIEWER" || role === "ADMIN") &&
          <button className={activeTab === "reviews" ? "nav-item active" : "nav-item"} aria-current={activeTab === "reviews" ? "page" : undefined} onClick={() => onNavigate("reviews")}><span aria-hidden="true">✓</span> Review queue</button>}
        {user?.roles.some(role => role === "EDITOR" || role === "ADMIN") &&
          <button className={activeTab === "taxonomy" ? "nav-item active" : "nav-item"} aria-current={activeTab === "taxonomy" ? "page" : undefined} onClick={() => onNavigate("taxonomy")}><span aria-hidden="true">⌘</span> Taxonomy</button>}
        {user?.roles.some(role => ["EDITOR", "REVIEWER", "ADMIN"].includes(role)) &&
          <button className={activeTab === "questions" ? "nav-item active" : "nav-item"} aria-current={activeTab === "questions" ? "page" : undefined} onClick={() => onNavigate("questions")}><span aria-hidden="true">?</span> Question bank</button>}
        {user?.roles.includes("ADMIN") &&
          <button className={activeTab === "users" ? "nav-item active" : "nav-item"} aria-current={activeTab === "users" ? "page" : undefined} onClick={() => onNavigate("users")}><span aria-hidden="true">♙</span> Users</button>}
      </nav>
      <div className="sidebar-footer"><div className="avatar" aria-hidden="true">{user?.name.slice(0, 1).toUpperCase() ?? "A"}</div><div className="account-copy"><strong>{user?.name}</strong><span>{user?.roles.join(" · ")}</span></div><button className="icon-button" title="Sign out" aria-label="Sign out" onClick={() => void logout()}>↗</button></div>
    </aside>
    <main id="main-content" className="main-area" tabIndex={-1}><header className="topbar"><div><span className="topbar-kicker">JIA / CONTENT OPERATIONS</span><span className="topbar-status"><i /> API workspace</span></div><span className="topbar-user">{user?.email}</span></header><div className="page-content" id="page-content">{children}</div></main>
  </div>;
}
