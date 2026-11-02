import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

export default function ProtectedRoute({ children }) {
  const { enabled, loading, user } = useAuth();
  const location = useLocation();
  if (!enabled) return <Navigate to="/match" replace />;
  if (loading) return <main className="auth-loading" aria-live="polite">Checking your sign-in…</main>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user?.role === 'ADMIN') return <Navigate to="/admin/dashboard" replace />;
  return children;
}
