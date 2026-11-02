import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import Icon from '../components/Icon.jsx';
import { Badge, Button, Card, CardHeading, EmptyState, Skeleton } from '../components/ui/Primitives.jsx';
import { ProgressRing } from '../components/ui/Charts.jsx';

export default function RoadmapPage({ guestItems = [], guestChecks = {}, onGuestToggle }) {
  const { enabled, accessToken, loading: authLoading } = useAuth();
  const [roadmap, setRoadmap] = useState(null); const [loading, setLoading] = useState(true); const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(''); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const load = useCallback(async () => {
    if (!enabled || !accessToken) { setLoading(false); return; }
    setLoading(true); setError(''); try { const result = await authApi.account('/roadmap', accessToken); setRoadmap(result); }
    catch (e) { setError(e.message); } finally { setLoading(false); }
  }, [enabled, accessToken]);
  useEffect(() => { if (!authLoading) load(); }, [authLoading, load]);
  async function generate() {
    setGenerating(true); setError(''); setNotice('');
    try { setRoadmap(await authApi.account('/roadmap', accessToken, { method: 'POST', body: {} })); setNotice('Your roadmap is ready and saved to your account.'); }
    catch (e) { setError(e.message); } finally { setGenerating(false); }
  }
  async function toggle(phaseId, itemId, itemType, complete) {
    setSaving(itemId); setError(''); setNotice('');
    try { setRoadmap(await authApi.account('/roadmap/progress', accessToken, { method: 'PUT', body: { phaseId, itemId, itemType, complete } })); setNotice('Roadmap progress saved.'); }
    catch (e) { setError(e.message); } finally { setSaving(''); }
  }
  if (authLoading || loading) return <section className="workspace-page intelligence-page"><header className="page-title"><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></header><Card><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /><Skeleton className="skeleton-line" /></Card></section>;
  if (!enabled) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">GROWTH · PERSONAL ROADMAP</p><h1>A practical plan, shaped around your direction.</h1><p>Small steps give your career exploration a useful next move.</p></header><Card><div className="roadmap-progress"><strong>{Object.values(guestChecks).filter(Boolean).length} of {guestItems.length} steps complete</strong><div className="progress-track"><span style={{width:`${guestItems.length?Math.round(Object.values(guestChecks).filter(Boolean).length/guestItems.length*100):0}%`}} /></div></div><div className="guest-roadmap-list">{guestItems.map((item)=><label className={`roadmap-task ${guestChecks[item.id]?'is-complete':''}`} key={item.id}><input type="checkbox" checked={Boolean(guestChecks[item.id])} onChange={(event)=>onGuestToggle?.(item,event.target.checked)} /><span><strong>{item.title}</strong><small>{item.detail}</small>{item.resource&&<span className="roadmap-task-resources"><a href={item.resource.url} target="_blank" rel="noopener noreferrer">{item.resource.provider} · {item.resource.level} · {item.resource.estimated_hours}h · {item.resource.cost} ↗</a></span>}</span></label>)}</div></Card><p className="tool-footnote">Your guest roadmap is saved in this browser. Sign in to use the full multi-phase plan and keep it with your account.</p></section>;
  if (!accessToken) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">GROWTH · PERSONAL ROADMAP</p><h1>A practical plan, shaped around your direction.</h1></header><Card><EmptyState icon="arrow" title="Sign in to save your roadmap" description="Your learning plan and completed tasks will stay with your account." action={<Link className="ui-button ui-button--primary" to="/login">Sign in</Link>} /></Card></section>;
  const phases = roadmap?.phases || [];
  const hasTargetRole = Boolean(roadmap?.hasTargetRole ?? roadmap?.targetRole?.code);
  return <section className="workspace-page intelligence-page roadmap-intelligence-page">
    <header className="page-title"><p className="eyebrow">GROWTH · PERSONAL ROADMAP</p><h1>Make progress one clear step at a time.</h1><p>Your plan combines your target role, weekly learning time, profile, resume review, assessment, and current skill gaps. Mark tasks as you complete them; the next phase unlocks when you finish its milestone.</p></header>
    {error && <div className="form-alert error" role="alert">{error}</div>}{notice && <div className="form-alert success" role="status">{notice}</div>}
    {!phases.length ? <Card><EmptyState icon="arrow" title={hasTargetRole ? `Build a roadmap for ${roadmap?.roleTitle || roadmap?.targetRole?.title || 'your target role'}` : 'Choose a target role first'} description={hasTargetRole ? 'Your target role is selected. Generate a practical plan from your profile, skills, and learning goals.' : 'Select a career role first. Then you can generate a practical plan from your profile and learning goals.'} action={!hasTargetRole ? <Link className="ui-button ui-button--secondary" to="/careers">Choose a target role</Link> : null} />{hasTargetRole && <div className="roadmap-generate-action"><Button variant="ai" icon="sparkles" disabled={generating} onClick={generate}>{generating ? 'Building your plan…' : 'Generate my roadmap'}</Button></div>}</Card> : <>
      <Card variant="hero" className="roadmap-hero"><div><p className="card-eyebrow">YOUR TARGET</p><h2>{roadmap.roleTitle}</h2><p>{roadmap.weeklyHours} focused learning hours per week · {roadmap.careerGoal}</p><div className="roadmap-hero-meta"><Badge tone="cyan">PERSONALIZED PLAN</Badge><span>{roadmap.completedItems} / {roadmap.totalItems} tasks and milestones complete</span></div></div><ProgressRing value={roadmap.completionPercent} size={124} label="complete" color="var(--accent-cyan)" /><Button variant="secondary" icon="refresh" disabled={generating} onClick={generate}>{generating ? 'Refreshing…' : 'Regenerate plan'}</Button></Card>
      <div className="roadmap-timeline">{phases.map((phase, phaseIndex) => <article key={phase.id} className={`roadmap-phase roadmap-phase--${phase.state}`}><div className="roadmap-phase-rail"><span>{phase.complete ? <Icon name="check" size={15} /> : String(phaseIndex + 1).padStart(2, '0')}</span></div><Card className="roadmap-phase-card"><header><div><p className="card-eyebrow">PHASE {String(phaseIndex + 1).padStart(2, '0')} · {phase.durationWeeks} WEEKS</p><h2>{phase.title}</h2><p>{phase.description}</p></div><Badge tone={phase.state === 'completed' ? 'cyan' : phase.state === 'active' ? 'violet' : 'neutral'}>{phase.state.toUpperCase()}</Badge></header>
        {phase.weeks?.map((week) => <div className="roadmap-week" key={week.week}><p className="roadmap-week-label">{week.title.toUpperCase()} · WEEKLY TASKS</p>{week.tasks?.map((task) => <label className={`roadmap-task ${task.complete ? 'is-complete' : ''}`} key={task.id}><input type="checkbox" checked={Boolean(task.complete)} disabled={phase.state === 'locked' || saving === task.id} onChange={(event) => toggle(phase.id, task.id, 'task', event.target.checked)} /><span><strong>{task.title}</strong><small>{task.detail}</small><em>~{task.estimatedHours} hours · {task.durationWeeks || 1} week{task.durationWeeks === 1 ? '' : 's'} at your learning pace</em>{task.resources?.length ? <span className="roadmap-task-resources">{task.resources.map((resource) => <a key={resource.url} href={resource.url} target="_blank" rel="noreferrer">{resource.provider || 'Learning resource'} · {resource.skill || resource.title} ↗</a>)}</span> : null}</span></label>)}</div>)}
        <label className={`roadmap-milestone ${phase.milestone?.complete ? 'is-complete' : ''}`}><input type="checkbox" checked={Boolean(phase.milestone?.complete)} disabled={phase.state === 'locked' || saving === phase.milestone?.id} onChange={(event) => toggle(phase.id, phase.milestone.id, 'milestone', event.target.checked)} /><span><small>PHASE MILESTONE</small><strong>{phase.milestone?.title}</strong></span></label>
      </Card></article>)}</div>
      <div className="roadmap-endcap"><Button variant="secondary" onClick={() => load()}>Refresh saved progress</Button></div>
    </>}
  </section>;
}
