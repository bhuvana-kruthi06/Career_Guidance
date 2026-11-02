import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import { Badge, Button, Card, CardHeading, EmptyState, Skeleton } from '../components/ui/Primitives.jsx';
import { ProgressRing } from '../components/ui/Charts.jsx';

const categories = ['All', 'Frontend', 'Backend', 'Database', 'Cloud', 'AI/ML', 'Tools', 'Soft skills'];

export default function SkillGapPage() {
  const { enabled, accessToken, loading: authLoading } = useAuth();
  const [data, setData] = useState(null); const [filter, setFilter] = useState('All');
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [saving, setSaving] = useState('');
  const refresh = useCallback(async () => {
    if (!enabled || !accessToken) { setLoading(false); return; }
    setLoading(true); setError('');
    try { setData(await authApi.account('/skill-gaps', accessToken)); } catch (e) { setError(e.message); } finally { setLoading(false); }
  }, [enabled, accessToken]);
  useEffect(() => { if (!authLoading) refresh(); }, [authLoading, refresh]);
  const skills = useMemo(() => (data?.skills || []).filter((item) => filter === 'All' || item.category === filter), [data, filter]);

  async function setStatus(skill, status) {
    const key = skill.name; setSaving(key); setError('');
    try { setData(await authApi.account('/skill-gaps/progress', accessToken, { method: 'PUT', body: { roleCode: data.targetRole.code, skillName: skill.name, status } })); }
    catch (e) { setError(e.message); } finally { setSaving(''); }
  }

  if (authLoading || loading) return <section className="workspace-page intelligence-page"><div className="page-title"><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></div><div className="intelligence-grid"><Card><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /><Skeleton className="skeleton-line" /></Card><Card><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></Card></div></section>;
  if (!enabled) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">GROWTH · SKILL GAP ANALYSIS</p><h1>Know what to build next.</h1><p>Sign in to compare your skills to your chosen role and keep progress across sessions.</p></header><Card><EmptyState icon="chart" title="Sign in to track skill progress" description="Skill development is stored securely with your account." action={<Link className="ui-button ui-button--primary" to="/login">Sign in</Link>} /></Card></section>;
  if (error && !data) return <section className="workspace-page intelligence-page"><div className="form-alert error" role="alert">{error}</div><Button variant="secondary" onClick={refresh}>Try again</Button></section>;
  if (!data?.targetRole?.code) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">GROWTH · SKILL GAP ANALYSIS</p><h1>Know what to build next.</h1><p>Pick a target role first. We’ll compare its O*NET skill requirements against your rated profile skills.</p></header><Card><EmptyState icon="compass" title="Choose your target role" description="Your skill map becomes specific as soon as you select a career direction." action={<Link className="ui-button ui-button--primary" to="/careers">Explore career roles</Link>} /></Card></section>;

  return <section className="workspace-page intelligence-page skill-gap-page">
    <header className="page-title"><p className="eyebrow">GROWTH · SKILL GAP ANALYSIS</p><h1>Your next skills, in focus.</h1><p>Compare your profile ratings with the selected role’s O*NET skill requirements. Self-ratings are a starting point, not a formal assessment.</p></header>
    {error && <div className="form-alert error" role="alert">{error}</div>}
    <div className="intelligence-grid skill-gap-overview">
      <Card variant="metric" className="skill-completion-card"><div><p className="card-eyebrow">ROLE READINESS</p><h2>{data.targetRole.title}</h2><p>{data.learned} of {data.total} tracked skills marked Learned</p></div><ProgressRing value={data.completionPercent} size={130} label="learned" color="var(--accent-cyan, #47d7c4)" /></Card>
      <Card><CardHeading eyebrow="PROGRESS SNAPSHOT" title="Your learning rhythm" /><div className="skill-counts"><div><strong>{data.learned}</strong><span>Learned</span></div><div><strong>{data.learning}</strong><span>Learning</span></div><div><strong>{data.total - data.learned - data.learning}</strong><span>To explore</span></div></div><div className="skill-legend"><span><i className="current" />Current self-rating</span><span><i className="required" />Role requirement</span></div></Card>
    </div>
    <Card className="skill-comparison-card"><CardHeading eyebrow="CURRENT VS REQUIRED" title="Role skill map" /><p className="muted-copy">Ratings use a 0–7 scale from your profile. Missing ratings show as 0; O*NET levels are comparative signals, not pass/fail thresholds.</p><div className="skill-filters" role="group" aria-label="Filter skill categories">{categories.map((item) => <button key={item} className={filter === item ? 'selected' : ''} aria-pressed={filter === item} onClick={() => setFilter(item)}>{item}</button>)}</div>
      <div className="skill-gap-list">{skills.length ? skills.slice(0, 35).map((skill) => <article className="skill-gap-item" key={skill.name}><div className="skill-gap-title"><div><strong>{skill.name}</strong><span>{skill.category}</span></div><div className="skill-gap-badges">{skill.resumeMentioned && <Badge tone="cyan">IN RESUME</Badge>}<Badge tone={skill.priority === 'Critical' ? 'rose' : skill.priority === 'Important' ? 'amber' : 'neutral'}>{skill.priority}</Badge></div></div><div className="skill-bars"><div className="skill-bar-row"><span>Current</span><div><i className="current" style={{ width: `${Math.min(100, skill.currentLevel / 7 * 100)}%` }} /></div><b>{Number(skill.currentLevel).toFixed(0)}</b></div><div className="skill-bar-row"><span>Required</span><div><i className="required" style={{ width: `${Math.min(100, skill.requiredLevel / 7 * 100)}%` }} /></div><b>{Number(skill.requiredLevel).toFixed(1)}</b></div></div><div className="skill-gap-footer"><span>{skill.gap ? `${skill.gap} level gap` : 'At or above role baseline'}{skill.resources?.length ? ` · ${skill.resources.length} curated resource${skill.resources.length === 1 ? '' : 's'}` : ''}</span><div><Button size="sm" variant={skill.status === 'Learning' ? 'primary' : 'secondary'} disabled={saving === skill.name || skill.status === 'Learning'} onClick={() => setStatus(skill, 'Learning')}>{saving === skill.name ? 'Saving…' : skill.status === 'Learning' ? 'Learning' : 'Start learning'}</Button><Button size="sm" variant={skill.status === 'Learned' ? 'ai' : 'ghost'} disabled={saving === skill.name || skill.status === 'Learned'} onClick={() => setStatus(skill, 'Learned')}>{skill.status === 'Learned' ? '✓ Learned' : 'Mark learned'}</Button></div></div>{skill.resources?.length ? <div className="skill-resources">{skill.resources.map((item) => <a key={item.url} href={item.url} target="_blank" rel="noreferrer">{item.provider || 'Learning resource'} · {item.title || skill.name} ↗</a>)}</div> : null}</article>) : <EmptyState icon="chart" title="No skills in this category" description="Choose another category to see role requirements." />}</div>
    </Card>
  </section>;
}
