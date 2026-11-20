import type { PropsWithChildren } from "react";
import { useAuth } from "../auth/AuthContext";
type Tab = "lessons" | "reviews";
export function AppShell({ activeTab, onNavigate, children }: PropsWithChildren<{ activeTab: Tab; onNavigate: (tab: Tab) => void }>) {
  const { user, logout } = useAuth();
  return <div className="app-shell">
    <aside className="sidebar"><div className="sidebar-brand"><div className="brand-mark small">J</div><div><strong>JIA Admin</strong><span>Academy workspace</span></div></div>
      <div className="sidebar-label">WORKSPACE</div><nav className="side-nav" aria-label="Main navigation">
        {user?.roles.some(role => role === "EDITOR" || role === "ADMIN") &&
          <button className={activeTab === "lessons" ? "nav-item active" : "nav-item"} onClick={() => onNavigate("lessons")}><span>▤</span> Lessons</button>}
        {user?.roles.some(role => role === "REVIEWER" || role === "ADMIN") &&
          <button className={activeTab === "reviews" ? "nav-item active" : "nav-item"} onClick={() => onNavigate("reviews")}><span>✓</span> Review queue</button>}
      </nav>
      <div className="sidebar-footer"><div className="avatar">{user?.name.slice(0, 1).toUpperCase() ?? "A"}</div><div className="account-copy"><strong>{user?.name}</strong><span>{user?.roles.join(" · ")}</span></div><button className="icon-button" title="Sign out" aria-label="Sign out" onClick={() => void logout()}>↗</button></div>
    </aside>
    <main className="main-area"><header className="topbar"><div><span className="topbar-kicker">JIA / CONTENT OPERATIONS</span><span className="topbar-status"><i /> API workspace</span></div><span className="topbar-user">{user?.email}</span></header><div className="page-content">{children}</div></main>
  </div>;
}