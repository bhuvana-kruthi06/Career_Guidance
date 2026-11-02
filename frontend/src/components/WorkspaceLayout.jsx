import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

export default function WorkspaceLayout({ children, navItems, page, setPage, savedCount, firstName, stage, connection, loadingSkills, onReconnect }) {
  const { enabled, user } = useAuth();
  return <main className="app-layout">
    <aside className="sidebar">
      <a className="brand" href="/overview" onClick={(event) => { event.preventDefault(); setPage('overview'); }} aria-label="Pathfinder home"><span className="brand-mark">P</span><span>pathfinder<small>CAREER GUIDANCE</small></span></a>
      <div className="nav-caption">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation">{navItems.map(([id, icon, label]) => <button key={id} className={page === id ? 'active' : ''} aria-current={page === id ? 'page' : undefined} onClick={() => setPage(id)}><span>{icon}</span>{label}{id === 'saved' && savedCount > 0 && <small>{savedCount}</small>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-tip"><span>✦</span><strong>Your next step starts here.</strong><small>Build a profile to uncover career paths shaped around you.</small><button onClick={() => setPage('explore')}>Start exploring →</button></div><div className="sidebar-user"><span className="avatar">{firstName ? firstName[0].toUpperCase() : 'Y'}</span><div><strong>{firstName || 'Your profile'}</strong><small>{stage || 'Career explorer'}</small></div></div></div>
    </aside>
    <div className="main-column"><header className="topbar">
      <div className="breadcrumbs">Pathfinder <span>/</span> {navItems.find(([id]) => id === page)?.[2]}</div>
      <div className={'connection-pill ' + connection} aria-live="polite"><span className="connection-dot" /><span>{connection === 'online' ? 'Matching service connected' : connection === 'checking' ? 'Checking service' : 'Ready for your profile'}</span>{connection === 'offline' && <button type="button" onClick={onReconnect} disabled={loadingSkills}>{loadingSkills ? 'Checking…' : 'Reconnect'}</button>}</div>
      {enabled && (user ? <Link className="account-link" to="/account">{user.email}</Link> : <Link className="sign-in-link" to="/login">Sign in</Link>)}
      <button className="header-settings" aria-label="Open settings" onClick={() => setPage('settings')}>⚙</button>
    </header>{children}</div>
  </main>;
}
