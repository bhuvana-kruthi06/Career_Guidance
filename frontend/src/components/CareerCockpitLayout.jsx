import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import Icon from './Icon.jsx';
import { Badge, Button, Drawer, IconButton, SearchInput } from './ui/Primitives.jsx';

const groups = [
  { label: 'CAREER', items: [
    { id: 'overview', label: 'Dashboard', icon: 'grid' },
    { id: 'profile', label: 'Profile', icon: 'user' },
    { id: 'assessment', label: 'Assessment', icon: 'chart' },
    { id: 'careerRoles', label: 'Career Roles', icon: 'compass' },
  ] },
  { label: 'GROWTH', items: [
    { id: 'skillGaps', label: 'Skill Gaps', icon: 'chart' },
    { id: 'roadmap', label: 'Roadmap', icon: 'arrow' },
    { id: 'projects', label: 'Projects', icon: 'briefcase' },
  ] },
  { label: 'PREPARATION', items: [
    { id: 'resume', label: 'Resume Analyzer', icon: 'file' },
    { id: 'interview', label: 'Interviews', icon: 'sparkles' },
  ] },
  { label: 'TRACKING', items: [
    { id: 'saved', label: 'Saved careers', icon: 'briefcase' },
    { id: 'applications', label: 'Applications', icon: 'briefcase' },
    { id: 'insights', label: 'Insights', icon: 'chart', accountOnly: true },
  ] },
  { label: 'PREFERENCES', items: [
    { id: 'settings', label: 'Settings', icon: 'settings' },
  ] },
];

const mobileItems = [
  { id: 'overview', label: 'Home', icon: 'grid' },
  { id: 'profile', label: 'Profile', icon: 'user' },
  { id: 'careerRoles', label: 'Roles', icon: 'compass' },
  { id: 'skillGaps', label: 'Skill Gaps', icon: 'chart' },
];

function destination(id) {
  return ({ overview: 'overview', profile: 'profile', assessment: 'assessment', careerRoles: 'careers', skillGaps: 'skill-gaps', roadmap: 'roadmap', projects: 'projects', resume: 'resume', interview: 'interview', jobMatcher: 'job-matcher', saved: 'saved', compare: 'compare', applications: 'applications', settings: 'settings', insights: 'account' })[id];
}

function routeFor(id) {
  return ({ overview: '/overview', profile: '/profile', assessment: '/assessment', careerRoles: '/careers', skillGaps: '/skill-gaps', roadmap: '/roadmap', projects: '/projects', resume: '/resume', interview: '/interview', jobMatcher: '/job-matcher', saved: '/saved', compare: '/compare', applications: '/applications', settings: '/settings', insights: '/account' })[id];
}

export default function CareerCockpitLayout({ children, page, setPage, savedCount, firstName, stage, connection, loadingSkills, onReconnect, theme, onThemeChange, openMentorOnLoad = false }) {
  const { enabled, user, logout, accessToken, loading: authLoading } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [mentorOpen, setMentorOpen] = useState(false);
  useEffect(() => { if (openMentorOnLoad) setMentorOpen(true); }, [openMentorOnLoad]);
  const [prompt, setPrompt] = useState('');
  const [mentorConversationId, setMentorConversationId] = useState(null);
  const [mentorMessages, setMentorMessages] = useState([]);
  const [mentorHistory, setMentorHistory] = useState([]);
  const [mentorSending, setMentorSending] = useState(false);
  const [mentorError, setMentorError] = useState('');
  const [mentorLoading, setMentorLoading] = useState(false);
  const displayName = firstName || user?.email?.split('@')[0] || 'Your profile';
  const allItems = useMemo(() => groups.flatMap((group) => group.items), []);
  const filteredItems = allItems.filter((item) => item.label.toLowerCase().includes(query.trim().toLowerCase()));

  useEffect(() => { setMobileOpen(false); setSearchOpen(false); setUserMenuOpen(false); }, [location.pathname]);
  useEffect(() => {
    if (!mentorOpen || !enabled || authLoading || !accessToken) return undefined;
    let active = true; setMentorLoading(true);
    authApi.account('/mentor', accessToken).then(async (items) => {
      if (!active) return; setMentorHistory(items || []);
      const selected = mentorConversationId ? items?.find((item) => item.conversationId === mentorConversationId) : items?.[0];
      if (selected) {
        const conversation = await authApi.account(`/mentor/${selected.conversationId}`, accessToken);
        if (active) { setMentorConversationId(selected.conversationId); setMentorMessages(conversation.messages || []); }
      }
    }).catch((error) => { if (active) setMentorError(error.message); }).finally(() => { if (active) setMentorLoading(false); });
    return () => { active = false; };
  }, [mentorOpen, enabled, authLoading, accessToken]);
  useEffect(() => {
    const onMentor = () => { setMentorOpen(true); setMentorSent(false); };
    window.addEventListener('pathfinder:open-mentor', onMentor);
    return () => window.removeEventListener('pathfinder:open-mentor', onMentor);
  }, []);
  useEffect(() => {
    const onKey = (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setSearchOpen(true); }
      if (event.key === 'Escape') { setSearchOpen(false); setMobileOpen(false); setMentorOpen(false); setNotificationsOpen(false); setUserMenuOpen(false); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  function selectPage(id) {
    const target = routeFor(id);
    if (target) navigate(target);
  }

  function openMentor() { setMentorOpen(true); setMentorError(''); }
  function startNewMentorChat() { setMentorConversationId(null); setMentorMessages([]); setPrompt(''); setMentorError(''); }
  async function submitPrompt(event) {
    event.preventDefault(); const message = prompt.trim(); if (!message || mentorSending) return;
    if (!enabled || !user || !accessToken) { setMentorError('Sign in to use your saved career context and keep this conversation.'); return; }
    setMentorSending(true); setMentorError('');
    try {
      const result = await authApi.account('/mentor', accessToken, { method: 'POST', body: { conversationId: mentorConversationId, message } });
      setMentorConversationId(result.conversationId); setMentorMessages((current) => [...current, ...(result.messages || [])]);
      setMentorHistory((current) => [{ conversationId: result.conversationId, title: result.conversationTitle || message.slice(0, 52), updatedAt: new Date().toISOString(), lastMessage: result.assistant?.summary || '' }, ...current.filter((item) => item.conversationId !== result.conversationId)]);
      setPrompt('');
    } catch (error) { setMentorError(error.message || 'The mentor could not respond. Try again.'); }
    finally { setMentorSending(false); }
  }
  async function selectMentorConversation(id) {
    if (!accessToken) return;
    setMentorLoading(true); setMentorError('');
    try { const conversation = await authApi.account(`/mentor/${id}`, accessToken); setMentorConversationId(id); setMentorMessages(conversation.messages || []); }
    catch (error) { setMentorError(error.message); } finally { setMentorLoading(false); }
  }
  function runMentorAction(route) {
    const pageByRoute = { '/roadmap': 'roadmap', '/resume': 'resume', '/projects': 'projects', '/interview': 'interview', '/skill-gaps': 'skillGaps', '/careers': 'careers' };
    if (pageByRoute[route]) { setMentorOpen(false); setPage(pageByRoute[route]); }
  }

  function NavButton({ item, isMobile = false }) {
    const target = destination(item.id);
    const isActive = item.id === 'overview' ? page === 'overview'
      : ['profile', 'assessment'].includes(item.id) ? page === item.id
        : item.id === 'careerRoles' ? ['careers', 'career-detail'].includes(page)
      : item.id === 'skillGaps' ? page === 'skill-gaps' : page === target;
    const disabled = item.soon || item.accountOnly && !enabled;
    const legacyLabel = ({ overview: 'Overview', profile: 'Career match', roadmap: 'My roadmap', interview: 'Interview studio' })[item.id];
    return <button key={item.id} type="button" aria-label={legacyLabel && !isMobile ? `${item.label} · ${legacyLabel}` : item.label} className={`nav-entry ${isActive ? 'active' : ''} ${disabled ? 'is-disabled' : ''} ${isMobile ? 'mobile-entry' : ''}`}
      aria-current={isActive ? 'page' : undefined} aria-disabled={disabled || undefined}
      title={disabled ? item.accountOnly ? 'Sign in to view insights' : 'Coming soon' : item.label}
      onClick={() => { if (!disabled) selectPage(item.id); }}>
      <Icon name={item.icon} size={18} /><span>{item.label}</span>{disabled ? <span className="nav-soon">{item.accountOnly ? 'LOCKED' : 'SOON'}</span> : null}
    </button>;
  }

  return <div className="app-layout cockpit-layout">
    {mobileOpen && <button type="button" className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
    <aside className={`sidebar cockpit-sidebar ${mobileOpen ? 'is-open' : ''}`}>
      <Link className="brand cockpit-brand" to="/overview" aria-label="Pathfinder dashboard"><span className="brand-mark"><Icon name="compass" size={19} /></span><span className="brand-copy">pathfinder<small>CAREER INTELLIGENCE</small></span></Link>
      <div className="workspace-switcher"><span className="workspace-avatar">{(firstName?.[0] || 'P').toUpperCase()}</span><span><strong>{firstName ? `${firstName}’s workspace` : 'Personal workspace'}</strong><small>Free plan · local profile</small></span><Icon name="chevron" size={14} /></div>
      <nav className="side-nav cockpit-nav" aria-label="Main navigation">
        {groups.map((group) => <div className="nav-group" key={group.label}><p>{group.label}</p>{group.items.map((item) => <NavButton key={item.id} item={item} />)}</div>)}
        <div className="nav-group nav-group-mentor"><p>YOUR GUIDE</p><button type="button" className="nav-entry mentor-nav" onClick={openMentor}><Icon name="sparkles" size={18} /><span>AI Mentor</span><span className="mentor-live-dot" /></button></div>
      </nav>
      <div className="sidebar-bottom cockpit-sidebar-bottom">
        <div className="sidebar-tip cockpit-tip"><span className="tip-icon"><Icon name="sparkles" size={16} /></span><strong>A clearer next step</strong><small>Small, steady progress can open new directions.</small><button type="button" onClick={openMentor}>Talk it through <Icon name="arrowRight" size={13} /></button></div>
        <div className="sidebar-user"><span className="avatar">{displayName[0]?.toUpperCase() || 'Y'}</span><span className="sidebar-user-copy"><strong>{displayName}</strong><small>{stage || (user?.email ? user.email : 'Personal profile')}</small></span><IconButton label="Open profile menu" onClick={() => setUserMenuOpen((value) => !value)}><Icon name="chevron" size={15} /></IconButton></div>
      </div>
    </aside>

    <div className="main-column cockpit-main">
      <header className="topbar cockpit-topbar">
        <IconButton className="mobile-menu-button" label="Open navigation" onClick={() => setMobileOpen(true)}><Icon name="menu" /></IconButton>
        <div className="topbar-context"><span className="context-mark"><Icon name="grid" size={15} /></span><span>{page === 'overview' ? 'Workspace' : 'Career workspace'} <b>/</b> {({ overview: 'Dashboard', explore: 'Career match', profile: 'Profile', assessment: 'Career assessment', roadmap: 'Growth plan', projects: 'Project recommender', resume: 'Resume analyzer', 'skill-gaps': 'Skill gap analysis', interview: 'Interview studio', 'job-matcher': 'Job description matcher', applications: 'Application tracker', saved: 'Saved careers', careers: 'Career roles', 'career-detail': 'Role details', compare: 'Compare paths', settings: 'Settings' })[page] || 'Workspace'}</span></div>
        <button className={`global-search ${searchOpen ? 'is-open' : ''}`} onClick={() => setSearchOpen((value) => !value)}><Icon name="search" size={16} /><span>Search workspace</span><kbd>⌘ K</kbd></button>
        <div className={`connection-pill ${connection}`} aria-live="polite"><span className="connection-dot" />{connection === 'online' ? 'Matcher ready' : connection === 'checking' ? 'Connecting' : 'Profile mode'}{connection === 'offline' && <button type="button" onClick={onReconnect} disabled={loadingSkills}>{loadingSkills ? 'Checking…' : 'Reconnect'}</button>}</div>
        <div className="topbar-actions">
          <Button variant="ai" size="sm" icon="sparkles" className="topbar-mentor" onClick={openMentor}>Ask AI</Button>
          <div className="popover-wrap"><IconButton label="Notifications" className="topbar-icon" onClick={() => { setNotificationsOpen((value) => !value); setUserMenuOpen(false); }}><Icon name="bell" size={17} /><i className="notification-dot" /></IconButton>{notificationsOpen && <div className="popover-panel notification-panel"><div className="popover-title"><strong>Notifications</strong><Badge tone="green">ALL CLEAR</Badge></div><p>Your workspace is up to date. Your next step is waiting when you are.</p><button onClick={() => { setNotificationsOpen(false); setPage('roadmap'); }}>Review my roadmap <Icon name="arrow" size={13} /></button></div>}</div>
          <IconButton label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} className="topbar-icon theme-toggle" onClick={() => onThemeChange(theme === 'dark' ? 'light' : 'dark')}><Icon name={theme === 'dark' ? 'sun' : 'moon'} size={17} /></IconButton>
          <div className="popover-wrap"><button type="button" className="profile-menu-trigger" aria-label="Open profile menu" onClick={() => { setUserMenuOpen((value) => !value); setNotificationsOpen(false); }}><span className="avatar">{displayName[0]?.toUpperCase() || 'Y'}</span><Icon name="chevron" size={14} /></button>{userMenuOpen && <div className="popover-panel profile-popover"><strong>{displayName}</strong><small>{user?.email || 'Guest workspace'}</small><button onClick={() => { setUserMenuOpen(false); setPage('settings'); }}>Workspace settings <Icon name="settings" size={14} /></button>{enabled && (user ? <button onClick={async () => { await logout(); window.location.assign('/match'); }}>Sign out <Icon name="arrowRight" size={14} /></button> : <Link to="/login">Sign in <Icon name="arrowRight" size={14} /></Link>)}</div>}</div>
        </div>
      </header>

      {searchOpen && <div className="search-scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) setSearchOpen(false); }}><section className="command-palette" role="dialog" aria-modal="true" aria-label="Search workspace"><SearchInput autoFocus value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && filteredItems[0]) { selectPage(filteredItems[0].id); setSearchOpen(false); } }} placeholder="Search pages and tools…" /><div className="command-results"><small>WORKSPACE DESTINATIONS</small>{filteredItems.filter((item) => !item.soon && !(item.accountOnly && !enabled)).map((item) => <button key={item.id} onClick={() => { selectPage(item.id); setSearchOpen(false); }}><Icon name={item.icon} size={16} /><span>{item.label}</span><kbd>↵</kbd></button>)}{!filteredItems.length && <p>No destinations match “{query}”.</p>}</div><footer><span><kbd>↑</kbd><kbd>↓</kbd> to navigate</span><span><kbd>ESC</kbd> to close</span></footer></section></div>}
      {children}
    </div>

    <nav className="mobile-bottom-nav" aria-label="Quick navigation">{mobileItems.map((item) => <NavButton key={item.id} item={item} isMobile />)}</nav>
    <button className="floating-mentor" aria-label="Open AI mentor" onClick={openMentor}><Icon name="sparkles" size={20} /></button>

    <Drawer open={mentorOpen} title="AI Mentor" onClose={() => setMentorOpen(false)}>
      <div className="mentor-intro"><span className="ai-orb"><Icon name="sparkles" size={19} /></span><Badge tone={accessToken ? 'cyan' : 'neutral'}>{accessToken ? 'CAREER CONTEXT' : 'SIGN IN REQUIRED'}</Badge><p>Ask about your role, skill gaps, resume signal, learning plan, projects, or interview preparation. Conversations are private to your account.</p></div>
      {accessToken && <div className="mentor-history-strip"><button type="button" className={!mentorConversationId ? 'selected' : ''} onClick={startNewMentorChat}>＋ New chat</button>{mentorHistory.slice(0, 5).map((item) => <button type="button" key={item.conversationId} className={mentorConversationId === item.conversationId ? 'selected' : ''} onClick={() => selectMentorConversation(item.conversationId)}>{item.title}</button>)}</div>}
      {!accessToken ? <div className="mentor-signin"><p>Sign in to connect your profile and keep mentor history.</p><Link to="/login" onClick={() => setMentorOpen(false)}>Sign in <Icon name="arrowRight" size={14} /></Link></div> : <>
        <div className="mentor-chat-messages" aria-live="polite">{mentorLoading && !mentorMessages.length ? <div className="mentor-loading"><span /><span /><span />Loading your career context…</div> : null}
          {!mentorMessages.length && !mentorLoading ? <div className="mentor-suggestions"><p>Try asking about</p>{['What should I learn next?', 'Review my resume.', 'Suggest projects for my profile.', 'Prepare me for a role-specific interview.'].map((question) => <button key={question} type="button" onClick={() => setPrompt(question)}>{question}<Icon name="arrowRight" size={14} /></button>)}</div> : null}
          {mentorMessages.map((message, index) => <article className={`mentor-message mentor-message--${message.role}`} key={`${message.createdAt || index}-${index}`}><span className="mentor-message-label">{message.role === 'user' ? 'YOU' : 'CAREER MENTOR'}</span><p>{message.content}</p>{message.structured && <div className="mentor-structured"><strong>Key findings</strong>{message.structured.keyFindings?.map((item) => <small key={item}>• {item}</small>)}<strong>Recommended actions</strong><div>{message.structured.recommendedActions?.map((action) => <button type="button" key={action.route} onClick={() => runMentorAction(action.route)}>{action.label} <Icon name="arrowRight" size={12} /></button>)}</div>{message.structured.provider === 'offline_fallback' && <small className="mentor-provider-note">Local career guidance fallback · no AI provider configured</small>}</div>}</article>)}
          {mentorSending && <div className="mentor-loading"><span /><span /><span />Thinking through your career context…</div>}
        </div>
        {mentorError && <p className="mentor-error" role="alert">{mentorError}</p>}
        <form className="mentor-composer" onSubmit={submitPrompt}><textarea value={prompt} maxLength={3000} onChange={(event) => setPrompt(event.target.value)} placeholder="Ask about your next step…" rows={3} aria-label="Ask the AI mentor" /><div><small>{enabled ? 'Your profile context stays server-side.' : 'Local account service unavailable.'}</small><Button variant="ai" size="sm" type="submit" disabled={!prompt.trim() || mentorSending} icon="arrowRight">{mentorSending ? 'Thinking…' : 'Send'}</Button></div></form>
      </>}
    </Drawer>
  </div>;
}
