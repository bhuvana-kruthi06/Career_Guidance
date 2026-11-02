import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';

export default function AuthPage({ mode }) {
  const isRegister = mode === 'register';
  const adminMode = mode === 'admin';
  const { enabled, loading, user, login, register, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [switchingAccount, setSwitchingAccount] = useState(false);
  const [error, setError] = useState('');
  const authenticatedAdminLogin = useRef(false);

  useEffect(() => {
    if (!adminMode || !enabled || loading || user?.role !== 'ADMIN' || authenticatedAdminLogin.current) return;
    setSwitchingAccount(true);
    logout().finally(() => setSwitchingAccount(false));
  }, [adminMode, enabled, loading, user, logout]);

  if (!enabled) return <main className="auth-page"><p className="eyebrow">GUEST MODE</p><h1>Accounts are turned off.</h1><p>You can still create a profile and explore career matches without signing in.</p><Link className="primary-button" to="/match">Continue as a guest <span>→</span></Link></main>;
  if (loading) return <main className="auth-loading">Checking your sign-in…</main>;
  if (adminMode && user?.role === 'ADMIN') return <main className="auth-loading">{switchingAccount ? 'Signing out of the current admin session…' : 'Preparing administrator sign-in…'}</main>;
  const roleMismatch = user && ((adminMode && user.role !== 'ADMIN') || (!adminMode && user.role === 'ADMIN'));
  if (user && !roleMismatch && !adminMode) return <Navigate to={user.role === 'ADMIN' ? '/admin/dashboard' : '/account'} replace />;

  async function switchAccount() {
    setSwitchingAccount(true);
    await logout();
    setSwitchingAccount(false);
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const credentials = { email: email.trim(), password, ...(isRegister ? { name: name.trim() } : {}) };
      if (adminMode) authenticatedAdminLogin.current = true;
      const session = isRegister ? await register(credentials) : await login(credentials);
      if (adminMode && session.user?.role !== 'ADMIN') {
        authenticatedAdminLogin.current = false;
        await logout();
        throw new Error('This account does not have administrator access.');
      }
      if (!adminMode && session.user?.role === 'ADMIN') { navigate('/admin/dashboard', { replace: true }); return; }
      if (isRegister) navigate('/onboarding', { replace: true });
      else if (adminMode) navigate('/admin/dashboard', { replace: true });
      else {
        let savedProfile = {};
        try { savedProfile = await authApi.account('/profile', session.accessToken); } catch { /* New or legacy accounts continue through profile setup. */ }
        navigate(location.state?.from || (Number(savedProfile.completeness || 0) >= 85 ? '/overview' : '/onboarding'), { replace: true });
      }
    } catch (requestError) {
      if (adminMode) authenticatedAdminLogin.current = false;
      setError(requestError.message || 'Your request could not be completed. Try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="auth-page">
    <Link className="auth-back" to={adminMode ? '/overview' : '/match'}>← {adminMode ? 'Career workspace' : 'Career match'}</Link>
    <p className="eyebrow">{adminMode ? 'PATHFINDER ADMIN CONSOLE' : 'PATHFINDER ACCOUNT'}</p>
    <h1>{isRegister ? 'Create your account.' : adminMode ? 'Administrator sign in.' : 'Welcome back.'}</h1>
    {roleMismatch && <section className="auth-session-notice" role="status">
      <strong>You’re signed in as {user.role === 'ADMIN' ? 'an administrator' : 'a user'}.</strong>
      <p>To use this {adminMode ? 'administrator' : 'user'} login, sign out of the current account first.</p>
      <button className="secondary-button" type="button" onClick={switchAccount} disabled={switchingAccount}>
        {switchingAccount ? 'Signing out…' : `Sign out and continue to ${adminMode ? 'admin' : 'user'} login`}
      </button>
    </section>}
    {!roleMismatch && <>
    <p>{isRegister ? 'Save your progress and keep your career guidance together.' : adminMode ? 'Sign in with an administrator account to continue.' : 'Sign in to continue to your account.'}</p>
    {error && <div className="auth-error" role="alert">{error}</div>}
    <form className="auth-form" onSubmit={submit}>
      {isRegister && <label className="field"><span>Full name</span><input autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} maxLength={80} required /></label>}
      <label className="field"><span>Email address</span><input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={254} required /></label>
      <label className="field"><span>Password{isRegister && <small>MINIMUM 12 CHARACTERS</small>}</span><input type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} value={password} onChange={(event) => setPassword(event.target.value)} minLength={isRegister ? 12 : undefined} maxLength={128} required /></label>
      <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'} <span>→</span></button>
    </form>
    {!isRegister && !adminMode && <p className="auth-reset-note">Forgot your password? Password reset is not available yet; contact the platform administrator.</p>}
    <p className="auth-switch">{isRegister ? 'Already have an account?' : 'New to Pathfinder?'}{' '}
      <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Create an account'}</Link>
    </p>
    {!isRegister && !adminMode && <p className="auth-admin-link"><Link to="/admin/login">Administrator sign in →</Link></p>}
    </>}
  </main>;
}
