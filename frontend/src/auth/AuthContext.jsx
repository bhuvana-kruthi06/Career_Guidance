import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { authApi } from '../api/auth.js';

const AuthContext = createContext({ enabled: false, loading: false, user: null, accessToken: null });

export function AuthProvider({ children }) {
  const authEnabled = import.meta.env.VITE_AUTH_ENABLED === 'true';
  const { pathname } = useLocation();
  const scope = pathname.startsWith('/admin') ? 'ADMIN' : 'USER';
  const [session, setSession] = useState({ scope: null, accessToken: null, expiresIn: 900, user: null });
  const [readyScope, setReadyScope] = useState(authEnabled ? null : scope);

  const storeSession = useCallback((value, sessionScope) => {
    setSession({ scope: sessionScope, accessToken: value.accessToken, expiresIn: value.expiresIn, user: value.user });
  }, []);
  const clearSession = useCallback((sessionScope) => setSession((current) => current.scope === sessionScope
    ? { scope: sessionScope, accessToken: null, expiresIn: 900, user: null } : current), []);
  const refreshAccessToken = useCallback(async (sessionScope = scope) => {
    if (!authEnabled) return false;
    try {
      storeSession(await authApi.refresh(sessionScope), sessionScope);
      return true;
    } catch {
      clearSession(sessionScope);
      return false;
    }
  }, [authEnabled, clearSession, scope, storeSession]);

  useEffect(() => {
    if (!authEnabled) { setReadyScope(scope); return undefined; }
    let active = true;
    setReadyScope(null);
    authApi.refresh(scope).then((value) => { if (active) storeSession(value, scope); })
      .catch(() => { if (active) clearSession(scope); })
      .finally(() => { if (active) setReadyScope(scope); });
    return () => { active = false; };
  }, [authEnabled, clearSession, scope, storeSession]);

  useEffect(() => {
    if (!authEnabled || session.scope !== scope || !session.user) return undefined;
    const delay = Math.max(30, session.expiresIn - 60) * 1000;
    const timer = window.setTimeout(() => { void refreshAccessToken(scope); }, delay);
    return () => window.clearTimeout(timer);
  }, [refreshAccessToken, scope, session.expiresIn, session.scope, session.user]);

  const login = useCallback(async (credentials) => { const value = await authApi.login(credentials, scope); storeSession(value, scope); return value; }, [scope, storeSession]);
  const register = useCallback(async (credentials) => { const value = await authApi.register(credentials, scope); storeSession(value, scope); return value; }, [scope, storeSession]);
  const logout = useCallback(async () => {
    try { await authApi.logout(scope); } catch { /* Local sign-out still completes when the API is offline. */ }
    clearSession(scope);
  }, [clearSession, scope]);

  const activeSession = session.scope === scope ? session : null;
  const value = useMemo(() => ({ enabled: authEnabled, loading: authEnabled && readyScope !== scope, scope,
    user: activeSession?.user || null, accessToken: activeSession?.accessToken || null, login, register, logout, refreshAccessToken }),
  [activeSession, authEnabled, readyScope, scope, login, register, logout, refreshAccessToken]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() { return useContext(AuthContext); }
