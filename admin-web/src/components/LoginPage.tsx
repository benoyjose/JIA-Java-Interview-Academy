import { useState } from "react";
import type { FormEvent } from "react";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    try { await login(email.trim(), password); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : "Sign-in failed. Please try again."); }
    finally { setBusy(false); }
  }
  return <main className="login-layout">
    <section className="login-brand"><div className="brand-mark">J</div><p className="eyebrow">JAVA INTERVIEW ACADEMY</p><h1>Build better technical interviews.</h1><p className="muted">Manage learning content, review revisions and publish lessons from one workspace.</p><div className="login-proof"><span /> Secure content operations</div></section>
    <section className="login-panel"><div className="login-card"><p className="eyebrow">ADMIN WORKSPACE</p><h2>Welcome back</h2><p className="muted">Sign in with your JIA administrator account.</p>
      <form onSubmit={submit} className="stack-form">
        <label>Email address<input type="email" autoComplete="username" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} /></label>
        <label>Password<input type="password" autoComplete="current-password" required maxLength={128} value={password} onChange={event => setPassword(event.target.value)} /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-primary button-wide" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
      </form><p className="security-note">Access is controlled by your JIA account permissions.</p>
    </div></section>
  </main>;
}