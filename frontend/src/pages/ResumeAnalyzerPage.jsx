import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { authApi } from '../api/auth.js';
import { Badge, Button, Card, CardHeading, EmptyState, Skeleton } from '../components/ui/Primitives.jsx';
import { ProgressRing } from '../components/ui/Charts.jsx';

const prettyDate = (value) => { try { return new Date(value).toLocaleString(); } catch { return ''; } };

export default function ResumeAnalyzerPage() {
  const { enabled, accessToken, loading: authLoading } = useAuth();
  const [history, setHistory] = useState([]); const [analysis, setAnalysis] = useState(null);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [file, setFile] = useState(null); const [loading, setLoading] = useState(true);
  const loadHistory = useCallback(async () => {
    if (!enabled || !accessToken) { setLoading(false); return; }
    setLoading(true); setError('');
    try { const items = await authApi.account('/resumes', accessToken); setHistory(items || []); if (items?.length && !analysis) setAnalysis(await authApi.account(`/resumes/${items[0].id}`, accessToken)); }
    catch (e) { setError(e.message); } finally { setLoading(false); }
  }, [enabled, accessToken]);
  useEffect(() => { if (!authLoading) loadHistory(); }, [authLoading, loadHistory]);

  async function submit(event) {
    event.preventDefault(); setError(''); setNotice('');
    const form = event.currentTarget;
    if (!file) { setError('Choose a PDF resume to analyze.'); return; }
    if (file.size > 5 * 1024 * 1024 || file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) { setError('Choose a PDF file smaller than 5 MB.'); return; }
    setBusy(true); setAnalysis(null);
    try { const result = await authApi.uploadResume(file, accessToken); setAnalysis(result); setHistory((items) => [{ id: result.id, targetRoleCode: result.targetRoleCode, targetRoleTitle: result.report?.targetRoleTitle || 'General resume review', atsScore: result.report?.atsScore || 0, createdAt: result.createdAt }, ...items].slice(0, 20)); setFile(null); form.reset(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function selectHistory(id) {
    setError(''); try { setAnalysis(await authApi.account(`/resumes/${id}`, accessToken)); } catch (e) { setError(e.message); }
  }
  async function applyToProfile() {
    setError(''); setNotice('');
    try { const result = await authApi.account(`/resumes/${analysis.id}/apply-to-profile`, accessToken, { method: 'PUT' }); window.dispatchEvent(new CustomEvent('pathfinder:profile-updated', { detail: result.profile })); setNotice(`Added ${result.updatedFields} missing profile field${result.updatedFields === 1 ? '' : 's'}.`); }
    catch (e) { setError(e.message); }
  }

  if (authLoading || loading) return <section className="workspace-page intelligence-page"><div className="page-title"><Skeleton className="skeleton-line" /><Skeleton className="skeleton-title" /></div><div className="intelligence-grid"><Card><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /><Skeleton className="skeleton-line" /></Card><Card><Skeleton className="skeleton-title" /><Skeleton className="skeleton-line" /></Card></div></section>;
  if (!enabled) return <section className="workspace-page intelligence-page"><header className="page-title"><p className="eyebrow">RESUME INTELLIGENCE</p><h1>Turn your resume into a clearer next step.</h1><p>Sign in to securely save your analysis history and profile data.</p></header><Card><EmptyState icon="file" title="Sign in to analyze a resume" description="Resume reports are saved to your private account." action={<Link className="ui-button ui-button--primary" to="/login">Sign in</Link>} /></Card></section>;

  const extracted = analysis?.extracted || {}; const report = analysis?.report || {};
  const sectionScores = Array.isArray(report.sectionScores) ? report.sectionScores.map((item) => [item.label, item.score]) : Object.entries(report.sectionScores || {});
  const roleFeedback = report.targetRoleFeedback ? [report.targetRoleFeedback] : report.roleFeedback || [];
  return <section className="workspace-page intelligence-page">
    <header className="page-title"><p className="eyebrow">PREPARATION · RESUME INTELLIGENCE</p><h1>A stronger story, backed by your experience.</h1><p>Upload a text-based PDF for a private, role-aware review. Files are processed on the backend and only extracted data and the report are retained.</p></header>
    <div className="intelligence-grid resume-top-grid">
      <Card variant="glass" className="resume-upload-card"><CardHeading eyebrow="NEW ANALYSIS" title="Review your resume" /><form onSubmit={submit} className="resume-upload-form"><label className="resume-file-input"><span className="resume-file-icon">↑</span><strong>{file?.name || 'Choose a PDF resume'}</strong><small>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB · ready to analyze` : 'Text-based PDF · up to 5 MB · 15 pages'}</small><input type="file" accept="application/pdf,.pdf" onChange={(event) => setFile(event.target.files?.[0] || null)} /></label><Button type="submit" variant="ai" icon="sparkles" disabled={busy || !file}>{busy ? 'Analyzing…' : 'Analyze resume'}</Button></form>{busy && <div className="resume-processing" role="status"><Skeleton className="skeleton-line" /><Skeleton className="skeleton-line" /><small>Extracting sections and checking target role alignment…</small></div>}</Card>
      {analysis ? <Card variant="metric" className="resume-score-card"><div><p className="card-eyebrow">ATS READINESS</p><h2>Your resume score</h2><p>Rule-based document review · {report.targetRoleTitle || 'General review'}</p></div><ProgressRing value={report.atsScore || 0} size={136} label="ATS score" color="var(--accent)" /></Card> : <Card className="resume-empty-card"><EmptyState icon="file" title="Your report will appear here" description="Get a clear score, extracted details, and improvements you can act on." /></Card>}
    </div>
    {error && <div className="form-alert error" role="alert">{error}</div>}{notice && <div className="form-alert success" role="status">{notice}</div>}
    {analysis && <>
      <div className="resume-report-header"><div><p className="card-eyebrow">ANALYSIS REPORT · {prettyDate(analysis.createdAt)}</p><h2>What your resume communicates</h2></div><Button variant="secondary" onClick={applyToProfile}>Fill missing profile details</Button></div>
      <div className="intelligence-grid resume-report-grid">
        <Card><CardHeading eyebrow="SECTION HEALTH" title="Score by section" /><div className="section-scores">{sectionScores.map(([name, score]) => <div className="section-score" key={name}><div><span>{name}</span><strong>{score}%</strong></div><div className="progress-track"><span style={{ width: `${score}%` }} /></div></div>)}</div></Card>
        <Card><CardHeading eyebrow="EXTRACTED PROFILE" title="Details we found" /><div className="extracted-details">{[['Name', extracted.name], ['Email', extracted.email], ['Phone', extracted.phone], ['LinkedIn', extracted.linkedIn || extracted.linkedin], ['GitHub', extracted.github]].map(([label, value]) => <div key={label}><small>{label}</small><span>{value || <em>Not found</em>}</span></div>)}</div><h3>Skills detected</h3><div className="intelligence-tags">{(extracted.skills || []).length ? extracted.skills.map((skill) => <Badge key={skill} tone="cyan">{skill}</Badge>) : <p>No listed skills detected. A clearly labeled skills section may help.</p>}</div>{extracted.education?.length ? <><h3>Education found</h3><ul className="analysis-list">{extracted.education.map((item) => <li key={item}>{item}</li>)}</ul></> : null}</Card>
        <Card><CardHeading eyebrow="ROLE ALIGNMENT" title="Keywords to consider" /><p className="muted-copy">{report.targetRoleTitle || 'General review'}</p><div className="intelligence-tags">{(report.missingKeywords || []).length ? report.missingKeywords.map((word) => <Badge key={word} tone="amber">{word}</Badge>) : <Badge tone="cyan">Strong tracked skill coverage</Badge>}</div>{roleFeedback.map((item) => <p className="analysis-note" key={item}>{item}</p>)}</Card>
        <Card><CardHeading eyebrow="CONTENT QUALITY" title="Bullets to strengthen" />{(report.weakBullets || []).length ? <ul className="analysis-list">{report.weakBullets.map((item) => <li key={item}><Badge tone="rose">VAGUE</Badge><span>{item}</span></li>)}</ul> : <EmptyState icon="check" title="No obvious vague bullets" description="Keep adding measurable outcomes as you gain experience." />}</Card>
        <Card className="resume-suggestions-card"><CardHeading eyebrow="NEXT IMPROVEMENTS" title="Specific ways to improve" />{(report.suggestions || []).map((item, i) => <div className="suggestion-row" key={item.title || item}><Badge tone={item.severity === 'Critical' ? 'rose' : item.severity === 'Important' ? 'amber' : 'neutral'}>{item.severity || String(i + 1).padStart(2, '0')}</Badge><p><strong>{item.title || 'Suggestion'}</strong>{item.detail ? ` · ${item.detail}` : item}</p></div>)}{(report.formattingSuggestions || []).map((item) => <div className="suggestion-row formatting" key={item}><span>↗</span><p>{item}</p></div>)}{report.scoreNote && <p className="analysis-note">{report.scoreNote}</p>}</Card>
      </div>
    </>}
    <Card className="resume-history-card"><CardHeading eyebrow="YOUR LIBRARY" title="Recent analyses" />{history.length ? <div className="resume-history-list">{history.map((item) => <button key={item.id} className={analysis?.id === item.id ? 'selected' : ''} onClick={() => selectHistory(item.id)}><span><strong>{item.targetRoleTitle}</strong><small>{prettyDate(item.createdAt)}</small></span><Badge tone="violet">{item.atsScore} ATS</Badge></button>)}</div> : <EmptyState icon="file" title="No saved analyses yet" description="Your reports will be available here after your first upload." />}</Card>
  </section>;
}
