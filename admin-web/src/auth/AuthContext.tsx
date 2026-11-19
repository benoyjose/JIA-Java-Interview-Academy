import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { PropsWithChildren } from "react";
import { api, clearStoredToken, getStoredToken, storeToken } from "../api/client";
import type { User } from "../api/types";

type AuthValue = { user: User | null; loading: boolean; login: (email: string, password: string) => Promise<void>; logout: () => Promise<void> };
const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (!getStoredToken()) { setLoading(false); return; }
    let active = true;
    api.me().then(value => { if (active) setUser(value); }).catch(() => {
      clearStoredToken(); if (active) setUser(null);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  const login = useCallback(async (email: string, password: string) => {
    const result = await api.login(email, password);
    storeToken(result.accessToken); setUser(result.user);
  }, []);
  const logout = useCallback(async () => {
    try { await api.logout(); } finally { clearStoredToken(); setUser(null); }
  }, []);
  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}