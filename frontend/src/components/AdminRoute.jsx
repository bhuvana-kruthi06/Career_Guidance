import { useEffect } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

function ForbiddenRedirect() {
  const navigate = useNavigate();
  useEffect(() => { const timer = window.setTimeout(() => navigate('/overview', { replace: true }), 2500); return () => window.clearTimeout(timer); }, [navigate]);
  return <main className="admin-forbidden"><span>403</span><h1>Administrator access required.</h1><p>This account cannot open the Admin Console. Taking you back to your career workspace…</p><button className="ui-button ui-button--primary" onClick={() => navigate('/overview', { replace: true })}>Return to dashboard</button></main>;
}

export default function AdminRoute({ children }) {
  const { enabled, loading, user } = useAuth();
  const location = useLocation();
  if (loading) return <main className="auth-loading">Checking administrator access…</main>;
  if (!enabled || !user) return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />;
  if (user.role !== 'ADMIN') return <ForbiddenRedirect />;
  return children;
}

export function UserExperienceRoute({ children }) {
  const { enabled, loading, user } = useAuth();
  const location = useLocation();
  if (loading) return <main className="auth-loading">Checking your sign-in…</main>;
  if (user?.role === 'ADMIN') return <Navigate to="/admin/dashboard" replace />;
  if (enabled && !user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}
