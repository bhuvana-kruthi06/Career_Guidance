import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import { Badge, Button, Card, CardHeading, EmptyState, Skeleton } from '../components/ui/Primitives.jsx';

const statuses = ['Planned', 'In Progress', 'Completed'];

export default function ProjectRecommenderPage() {
  const { enabled, accessToken, loading: authLoading } = useAuth(); const [projects, setProjects] = useState([]); const [role, setRole] = useState(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState('');
  const load = useCallback(async () => {
    if (!enabled || !accessToken) { setLoading(false); return; }
    setLoading(true); setError(''); try { const response = await authApi.account('/projects', accessToken); setProjects(response.projects || []); setRole(response.targetRole); }
    catch (e) { setError(e.message); } finally { setLoading(false); }
  }, [enabled, accessToken]);
  useEffect(() => { if (!authLoading) load(); }, [authLoading, load]);
  async function save(project) {
    setBusy(project.projectKey); setError(''); setNotice('');
    try { const updated = await authApi.account('/projects', accessToken, { method: 'POST', body: { projectKey: project.projectKey } }); setProjects((current) => current.map((item) => item.projectKey === updated.projectKey ? updated : item)); setNotice(`${project.title} added to your project plan.`); }
    catch (e) { setError(e.message); } finally { setBusy(''); }
  }
  async function setStatus(project, status) {
    setBusy(project.projectKey); setError(''); setNotice('');
    try { const updated = await authApi.account(`/projects/${project.projectKey}/status`, accessToken, { method: 'PUT', body: { status } }); setProjects((current) => current.map((item) => item.projectKey === updated.projectKey ? updated : item)); setNotice(status === 'Completed' ? 'Project marked complete. Related roadmap progress was updated.' : 'Project status saved.'); }
    catch (e) { setError(e.message); } finally { setBusy(''); }
  }
  if (authLoading || loading) return <section className="workspace-page intelligence-page"><header className="page-title"><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></header><div className="project-recommendation-grid">{[1,2].map(i => <Card key={i}><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></Card>)}</div></section>;
  if (!enabled) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">GROWTH · PROJECT RECOMMENDER</p><h1>Build evidence for the work you want.</h1></header><Card><EmptyState icon="briefcase" title="Sign in to save project progress" description="Project statuses connect to your roadmap and dashboard." action={<Link className="ui-button ui-button--primary" to="/login">Sign in</Link>} /></Card></section>;
  return <section className="workspace-page intelligence-page project-recommender-page">
    <header className="page-title"><p className="eyebrow">GROWTH · PROJECT RECOMMENDER</p><h1>Build proof for your next role.</h1><p>These portfolio ideas use your target role and priority skill gaps. Scope each project so you can finish it and explain your decisions.</p></header>
    {error && <div className="form-alert error" role="alert">{error}</div>}{notice && <div className="form-alert success" role="status">{notice}</div>}
    {!role?.code ? <Card><EmptyState icon="compass" title="Select a target role first" description="Your recommendations are matched to one career direction and its skill requirements." action={<Link className="ui-button ui-button--primary" to="/careers">Explore career roles</Link>} /></Card> : <>
      <Card variant="glow" className="project-role-banner"><div><p className="card-eyebrow">PROJECTS FOR YOUR TARGET</p><h2>{role.title}</h2><p>Pick one idea that fits your current time and experience. You can grow it with stretch features later.</p></div><Badge tone="cyan">ROLE MATCHED</Badge></Card>
      <div className="project-recommendation-grid">{projects.map((project, index) => <Card className="recommended-project-card" key={project.projectKey}><div className="project-card-top"><span className="project-index">0{index + 1}</span><Badge tone={project.status === 'Completed' ? 'cyan' : project.status === 'In Progress' ? 'violet' : 'neutral'}>{project.status}</Badge></div><CardHeading eyebrow={project.difficulty + ' · ' + project.roleFamily.toUpperCase()} title={project.title} />
        <div className="project-stack">{project.stack?.map(skill => <Badge key={skill} tone="neutral">{skill}</Badge>)}</div>
        {project.gapSkillsToPractice?.length ? <p className="project-gap-note">Practise: {project.gapSkillsToPractice.join(' · ')}</p> : null}
        <div className="project-feature-block"><h3>Core features</h3><ul>{project.features?.map(item => <li key={item}>{item}</li>)}</ul></div>
        <details className="project-expand"><summary>Stretch features and resume bullets</summary><div><h3>Stretch features</h3><ul>{project.stretch?.map(item => <li key={item}>{item}</li>)}</ul><h3>Evidence for your resume</h3><ul>{project.resumeBullets?.map(item => <li key={item}>{item}</li>)}</ul><h3>Suggested repository structure</h3><div className="project-structure">{project.structure?.map(path => <code key={path}>{path}</code>)}</div></div></details>
        <div className="project-card-actions">{!project.saved ? <Button variant="ai" icon="plus" disabled={busy === project.projectKey} onClick={() => save(project)}>{busy === project.projectKey ? 'Saving…' : 'Add to plan'}</Button> : <label className="project-status-control"><span>Project status</span><select value={project.status} disabled={busy === project.projectKey} onChange={(event) => setStatus(project, event.target.value)}>{statuses.map(status => <option key={status}>{status}</option>)}</select></label>}</div>
      </Card>)}</div>
      <div className="project-recommender-footer"><span>Completing a project also marks the matching portfolio task on your roadmap.</span><Link to="/roadmap">Open roadmap →</Link></div>
    </>}
  </section>;
}
