import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { readStorage } from '../lib/storage.js';

export default function AccountPage() {
  const { accessToken, user, logout } = useAuth();
  const navigate = useNavigate();
  const [account, setAccount] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [sessions, setSessions] = useState([]);
  const [selectedSession, setSelectedSession] = useState(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const chartData = sessions.slice().reverse().map((session) => {
    const ratings = Object.values(session.skills || {}).map(Number).filter((value) => Number.isFinite(value) && value >= 1 && value <= 5);
    return { id: session.id, date: session.createdAt, averageRating: ratings.length ? ratings.reduce((sum, value) => sum + value, 0) / ratings.length : 0,
      topScore: Number(session.results?.[0]?.match_score) || 0 };
  });
  const chartPoints = (key) => chartData.map((point, index) => {
    const x = chartData.length < 2 ? 200 : 24 + index * (352 / (chartData.length - 1));
    const y = 148 - (key === 'averageRating' ? point.averageRating / 5 * 100 : point.topScore) * 1.18;
    return `${x},${y}`;
  }).join(' ');

  useEffect(() => {
    let active = true;
    Promise.all([authApi.me(accessToken), authApi.account('/sessions', accessToken)])
      .then(([value, history]) => { if (active) { setAccount(value); setSessions(history); } })
      .catch((requestError) => { if (active) setError(requestError.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [accessToken]);

  async function signOut() {
    await logout();
    navigate('/match', { replace: true });
  }

  async function importLocalData() {
    setBusy(true); setMessage('');
    try {
      const state = readStorage('pathfinder-profile-state', {}, (value) => value && typeof value === 'object', 'sessionStorage');
      const savedCareers = readStorage('pathfinder-saved', [], Array.isArray);
      const checked = readStorage('pathfinder-roadmap', {}, (value) => value && typeof value === 'object' && !Array.isArray(value));
      const roadmap = Object.entries(checked).map(([key, complete]) => ({ key, complete: Boolean(complete), completedAt: null }));
      const result = await authApi.account('/import-local', accessToken, { method: 'POST', body: {
        profile: state.profile || null, savedCareers, roadmap, skills: state.ratings || null,
        interests: state.interests || [], results: state.results || [],
      } });
      setMessage(result.imported ? 'Your browser data was copied to this account. The local copy is still available.' : 'This account has already imported local data.');
    } catch (requestError) { setMessage(requestError.message || 'Could not import local data.'); }
    finally { setBusy(false); }
  }

  async function deleteData() {
    if (!window.confirm('Delete your account and all saved profile, match, career, and roadmap data? This cannot be undone.')) return;
    setBusy(true);
    try { await authApi.account('', accessToken, { method: 'DELETE' }); await logout(); navigate('/match', { replace: true }); }
    catch (requestError) { setMessage(requestError.message || 'Could not delete your data.'); }
    finally { setBusy(false); }
  }

  return <main className="auth-page account-page">
    <Link className="auth-back" to="/match">← Career match</Link>
    <p className="eyebrow">YOUR ACCOUNT</p>
    <h1>Account details.</h1>
    {loading ? <p className="auth-status" role="status">Loading your account…</p>
      : error ? <div className="auth-error" role="alert">{error} <Link to="/login">Sign in again</Link></div>
        : <section className="account-card" aria-label="Account information">
          <div><span>Email</span><strong>{account?.email || user?.email}</strong></div>
          <div><span>Role</span><strong>{account?.role || user?.role}</strong></div>
          <div className="account-actions"><h2>Your career history</h2>
            {sessions.length ? <><div className="progress-chart"><svg viewBox="0 0 400 170" role="img" aria-label="Profile skill ratings and top career match scores across saved sessions"><line x1="24" y1="148" x2="376" y2="148" /><line x1="24" y1="30" x2="24" y2="148" /><polyline points={chartPoints('averageRating')} className="rating-line" /><polyline points={chartPoints('topScore')} className="match-line" />{chartData.map((point,index)=>{const x=chartData.length<2?200:24+index*(352/(chartData.length-1));return <circle key={point.id} cx={x} cy={148-(point.averageRating/5*100)*1.18} r="3" className="rating-dot" />;})}</svg><div className="chart-legend"><span>Average skill rating (1–5)</span><span>Top career match score (%)</span></div></div><p className="chart-note">Trend points come from the ratings and results stored in each match session.</p><ul className="session-list">{sessions.map((session) => <li key={session.id}><button type="button" className="session-link" aria-expanded={selectedSession?.id===session.id} onClick={()=>setSelectedSession(selectedSession?.id===session.id?null:session)}>{new Date(session.createdAt).toLocaleDateString()} · {session.results?.[0]?.occupation_title || 'Career match'}</button></li>)}</ul>{selectedSession && <div className="session-detail"><h3>Saved match · {new Date(selectedSession.createdAt).toLocaleDateString()}</h3>{selectedSession.results?.length ? selectedSession.results.slice(0,5).map((career)=><p key={career.onet_soc_code}>{career.rank}. {career.occupation_title} · {Math.round(career.match_score)}% profile similarity</p>) : <p>This saved session did not return any matches.</p>}<button type="button" className="text-button" disabled={busy} onClick={async()=>{setBusy(true);try{await authApi.account('/sessions/'+selectedSession.id,accessToken,{method:'DELETE'});setSessions((current)=>current.filter((item)=>item.id!==selectedSession.id));setSelectedSession(null);}catch(e){setMessage(e.message||'Could not delete session.');}finally{setBusy(false);}}}>Delete this session</button></div>}</> : <p>No saved match sessions yet. Run a match while signed in to start tracking progress.</p>}
            <button className="secondary-button" type="button" disabled={busy} onClick={importLocalData}>Import browser data</button>
            <button className="text-button" type="button" disabled={busy} onClick={deleteData}>Delete my data</button>
            {message && <p className="auth-status" role="status">{message}</p>}
          </div>
          <button className="secondary-button" type="button" onClick={signOut}>Sign out</button>
        </section>}
  </main>;
}
